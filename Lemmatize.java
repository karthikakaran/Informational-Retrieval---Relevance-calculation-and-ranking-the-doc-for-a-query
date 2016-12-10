//===========================================================================================================================
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;
import java.util.Map.Entry;
import java.util.regex.Pattern;

import edu.stanford.nlp.ling.CoreAnnotations.LemmaAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.SentencesAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TokensAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.StringUtils;

public class Lemmatize {
	public static StanfordCoreNLP pipeline;
	public static TreeMap<String, DictionaryClass> info = new TreeMap<>();
	private static TreeMap<Integer, String> docMaxFreq = new TreeMap<>();
	private static HashMap<Integer, HashMap<String, Integer>> termFqMap = new HashMap<>();
	private static TreeMap<String, Integer> queryTermFqMap = new TreeMap<>();
	private static int queryCollSize = 1, queryDf = 1, queryMaxTf = 1;
	private static TreeMap<String, TreeMap<String, Double>> queryWeightsMap = new TreeMap<>();
	private static TreeMap<Integer, TreeMap<String, Double>> docWeightsMap = new TreeMap<>();
	private static TreeMap<Integer, Double> queryDocScores = new TreeMap<>();
	
	private static TreeMap<String, TreeMap<String, Double>> queryWeightsMapW2 = new TreeMap<>();
	private static TreeMap<Integer, TreeMap<String, Double>> docWeightsMapW2 = new TreeMap<>();
	private static TreeMap<Integer, Double> queryDocScoresW2 = new TreeMap<>();
	
	private static double[][] cosineScoreW1; 
	private static double[][] cosineScoreW2;
	//To create lemmas from the tokens 
	public static void lemmatizer(ArrayList<String> tokenisedWords, boolean queryOrWord, int avgDocLen, int collectionSize, ArrayList<Integer> docList) throws IOException {
		StanfordLemmatizer();
		int index = 0, count = 0, queryId = 0;
		String lemma = "", line = "";
		if (queryOrWord) {
			cosineScoreW1 = new double[collectionSize][20]; 
			cosineScoreW2 = new double[collectionSize][20];
			tokenisedWords = new ArrayList<>();
			//docWeight1(docList, collectionSize);
			//docWeight2(docList, avgDocLen, collectionSize);
			Scanner in = new Scanner(new File("hw3.queries"));
			while(in.hasNextLine()) {
				line = in.nextLine();
				if(Pattern.matches("Q\\d+:", line)){
					
					queryId = Integer.parseInt(line.substring(1,line.length()-1));System.out.println(queryId);
				}
				else if(line.length() == 0){
					while (index < tokenisedWords.size()) { 
						lemma = lemmatize(tokenisedWords.get(index++));
						if(!queryTermFqMap.containsKey(lemma)) {
							queryTermFqMap.put(lemma, 1);
						} else {
							count = queryTermFqMap.get(lemma);
							queryTermFqMap.put(lemma, count++);
							if(queryMaxTf < count)
								queryMaxTf = count;
						}
					}
					weightScheme1(docList, collectionSize, queryId);
					weightScheme2(docList, avgDocLen, collectionSize, queryId);
					tokenisedWords.clear();
					queryTermFqMap.clear();
					queryMaxTf = 1;
					index = 0;
					
					orderTheDocs(docList, queryId);
					orderTheDocsW2(docList, queryId);
				} else {
					tokenisedWords.addAll(Tokenize.queryTokenizer(line));
				}
			}
		} else {
			String[] term;
			int docId = 0, docLen = 0;
			index = 0;
			Integer maxTf = 0;
			int termFrequency = 1, docFrequency = 1;
			DictionaryClass dcNew;
			String stemToken = "";
			ArrayList<String> arrayList = new ArrayList<>();
			HashMap<String, Integer> h = null ;
			while (index < tokenisedWords.size()) { 
				termFrequency = 1;
				docFrequency = 1;
				term = tokenisedWords.get(index++).split(" ");
				docId = Integer.parseInt(term[1]);
				docLen = Integer.parseInt(term[2]);
				stemToken = lemmatize(term[0]);
				arrayList.add(stemToken + " " + docId + " " + docLen);
				if (!termFqMap.containsKey(docId)){
					h = new HashMap<>();
					h.put(stemToken, 1);
					termFqMap.put(docId, h);
				} else {
					h = termFqMap.get(docId);
					if(!h.containsKey(stemToken)) {
						h.put(stemToken, 1);
					} else {
						h.put(stemToken,h.get(stemToken) + 1);
					}
				}
			}
			
			DocDetails docDet;
			int i = 0;
			while (arrayList.size() > i) {
				term = arrayList.get(i++).split(" ");
				stemToken = term[0]; docId = Integer.parseInt(term[1]); docLen = Integer.parseInt(term[2]);
				
				for(Entry<String, Integer> item : Tokenize.sortByValue(termFqMap.get(docId)).entrySet()) {
					maxTf = item.getValue();
					docMaxFreq.put(docId, maxTf+"-"+docLen);
					break;
				}
				
				if (!info.containsKey(stemToken))
					info.put(stemToken, new DictionaryClass(stemToken, docId, docLen, maxTf, termFrequency, docFrequency));
				else {
					dcNew = info.get(stemToken);
					TreeMap<Integer, DocDetails> postingListObj = dcNew.getPostingList(); 
					if (postingListObj.containsKey(docId)) {
						docDet = postingListObj.get(docId);
						docDet.setTermFrequency(docDet.getTermFrequency() + 1);
						postingListObj.put(docId, docDet);
						dcNew.setPostingList(postingListObj);
						info.put(stemToken, dcNew);
					} else {
						postingListObj.put(docId, new DocDetails(docLen, maxTf, termFrequency));
						dcNew.setPostingList(postingListObj);
						dcNew.setDocFrequency(dcNew.getDocFrequency() + 1);
						info.put(stemToken, dcNew);
					}
				}
			}
		}
	}
	
	private static void orderTheDocs(ArrayList<Integer> docIdList, int queryId) throws IOException {
		System.out.println("Weighting scheme 1 ::");
		TreeMap<String, Double> qscoreMap = new TreeMap<>();
		qscoreMap = queryWeightsMap.get("Q"+queryId);
		System.out.println("Q"+queryId);
		for (Entry<String, Double> item : qscoreMap.entrySet()) {
			if (item.getValue() > 0)
			System.out.print(item.getKey() + "-" + item.getValue() + "  ");
		}
		System.out.println();
		for(int i = 0; i < cosineScoreW1.length; i++) {
			queryDocScores.put(docIdList.get(i), cosineScoreW1[i][queryId - 1]);
		}
		int top5 = 1;
		for(Entry<Integer, Double> item : Tokenize.sortByValue(queryDocScores).entrySet()) {
			int docId = item.getKey();
			String extern = "cranfield"+StringUtils.padLeft(String.valueOf(docId), 4, '0');
			String title = findTitle(extern);
			System.out.println("Rank :: "+ top5 + " Score :: "+ item.getValue() + " External Identifier ::" + extern + " Title :: " + title);
			printDocVector(docWeightsMap, docId);
			top5++;
			if(top5 == 6)
				break;
		}
	}
	private static void orderTheDocsW2(ArrayList<Integer> docIdList, int queryId) throws IOException {
		System.out.println("Weighting scheme 2 ::");
		TreeMap<String, Double> qscoreMap = new TreeMap<>();
		qscoreMap = queryWeightsMapW2.get("Q"+queryId);
		System.out.println("Q"+queryId);
		for (Entry<String, Double> item : qscoreMap.entrySet()) {
			if (item.getValue() > 0)
			System.out.print(item.getKey() + "-" + item.getValue() + "  ");
		}
		System.out.println();
		for(int i = 0; i < cosineScoreW2.length; i++) {
			queryDocScoresW2.put(docIdList.get(i), cosineScoreW2[i][queryId - 1]);
		}
		int top5 = 1;
		for(Entry<Integer, Double> item : Tokenize.sortByValue(queryDocScoresW2).entrySet()) {
			int docId = item.getKey();
			String extern = "cranfield"+StringUtils.padLeft(String.valueOf(docId), 4, '0');
			String title = findTitle(extern);
			System.out.println("Rank :: "+ top5 + " Score :: "+ item.getValue() + " External Identifier ::" + extern + " Title :: " + title);
			printDocVector(docWeightsMapW2, docId);
			top5++;
			if(top5 == 6)
				break;
		}
	}

	private static void printDocVector(TreeMap<Integer, TreeMap<String, Double>> docWeightsMap, int docId) {
		System.out.println("Vector rep of Doc - " + docId);
		for(Entry<String, Double> item : docWeightsMap.get(docId).entrySet()) {
			if(item.getValue() > 0)
				System.out.print(item.getKey() + "-" + item.getValue() + "  ");
		}
		System.out.println();
	}
	private static String findTitle(String docId) throws IOException {
		File fileName = new File("Cranfield/"+docId);
		FileInputStream fileInputStream = new FileInputStream(fileName);
		byte[] bytesLength = new byte[(int) fileName.length()];
		fileInputStream.read(bytesLength);
		fileInputStream.close();
		String docData = new String(bytesLength, "UTF-8");
		String title = docData.substring(docData.indexOf("<TITLE>"),
				docData.lastIndexOf("</TITLE>"));
		String formattedTitle = title.replace("\n", "").replace("\r", "");
		formattedTitle = formattedTitle.replaceAll("<TITLE>", "");
		return formattedTitle;
	}
	
	public static void weightScheme1(ArrayList<Integer> docIdList, int collectionSize, int queryId) {
		//W1 = (0.4 + 0.6 * log (tf + 0.5) / log (maxtf + 1.0)) * (log (collectionsize / df)/ log (collectionsize))
		TreeMap<String, Double> scoreMap = new TreeMap<>();
		TreeMap<String, Double> qscoreMap = new TreeMap<>();
		double[] normScoreMap = new double[info.size()];
		double[] normQscoreMap = new double[info.size()];
		double finalScore = 0.0, weight = 0, qweight = 0;
		Integer maxTf = 0, tf = 0, df = 0, qtf = 0, iDoc = 0, qdf = 0;
		for (int docId : docIdList) {
			maxTf = Integer.parseInt(docMaxFreq.get(docId).split("-")[0]);
			for (String terms : info.keySet()) {
				//Dictionary calc
				//if(docWeightsMap.size() < docIdList.size()) {
					tf = termFqMap.get(docId).get(terms);
					tf = (tf == null) ? 0 : tf;
					df = info.get(terms).getDocFrequency();
					weight = (0.4 + 0.6 * Math.log(tf + 0.5) / Math.log(maxTf + 1.0)) * (Math.log(collectionSize / df)/ Math.log(collectionSize));
					scoreMap.put(terms, weight);
				//}
				//Query calc
				//if(iDoc == 0) {
					qtf = queryTermFqMap.get(terms);
					qtf = (qtf == null) ? 0 : qtf;
					qdf = (qtf == 0) ? 0 : 1;
					qweight = Math.max((0.4 + 0.6 * Math.log(qtf + 0.5) / Math.log(queryMaxTf + 1.0)), 0);
					qscoreMap.put(terms, qweight);
					
				//}
			}
			//if(docWeightsMap.size() < docIdList.size()) 
				docWeightsMap.put(docId, scoreMap);
			scoreMap = docWeightsMap.get(docId);
			normScoreMap = normalise(scoreMap);
			//if(iDoc == 0) {
				queryWeightsMap.put("Q"+queryId, qscoreMap);
				normQscoreMap = normalise(qscoreMap);
			//}
			finalScore = 0.0;
			for(int i = 0; i < normScoreMap.length; i++) {
				
				finalScore += (normScoreMap[i] * normQscoreMap[i]);
			}
			//System.out.println(docId + " "+ queryId+" " + finalScore);
			cosineScoreW1[iDoc++][queryId - 1] = finalScore;
		}
	}
	
	public static void weightScheme2(ArrayList<Integer> docIdList, int avgDocLen, int collectionSize, int queryId) {
		//W2 = (0.4 + 0.6 * (tf / (tf + 0.5 + 1.5 * (doclen / avgdoclen))) * log (collectionsize / df)/	log (collectionsize))
		TreeMap<String, Double> scoreMap = new TreeMap<>();
		TreeMap<String, Double> qscoreMap = new TreeMap<>();
		double[] normScoreMap = new double[info.size()];
		double[] normQscoreMap = new double[info.size()];
		double finalScore = 0.0, weight = 0, qweight = 0;
		Integer tf = 0, df = 0, qtf = 0, docLen = 0, iDoc = 0, qdf = 0;
		for (int docId : docIdList) {
			docLen = Integer.parseInt(docMaxFreq.get(docId).split("-")[1]);
			for (String terms : info.keySet()) {
				//Dictionary calc
				//if(docWeightsMapW2.size() < docIdList.size()) {
					tf = termFqMap.get(docId).get(terms);
					tf = (tf == null) ? 0 : tf;
					df = info.get(terms).getDocFrequency();
					weight = (0.4 + 0.6 * ( tf / (tf + 0.5 + 1.5 * (docLen / avgDocLen))) * Math.log (collectionSize / df) / Math.log (collectionSize));
					scoreMap.put(terms, weight);
				//}
				//Query calc
				//if(iDoc == 0) {
					qtf = queryTermFqMap.get(terms);
					qtf = (qtf == null) ? 0 : qtf;
					qdf = (qtf == 0) ? 0 : 1;
					//average length of all queries is assumed to be 20
					qweight = (0.4 + 0.6 * ( qtf / (qtf + 0.5 + 1.5 * (queryTermFqMap.size() / 10))));
					
					qscoreMap.put(terms, qweight);
				//}
			}
			//if(docWeightsMapW2.size() < docIdList.size()) 
				docWeightsMapW2.put(docId, scoreMap);
			scoreMap = docWeightsMapW2.get(docId);
			normScoreMap = normalise(scoreMap);
			//if(iDoc == 0) {
				queryWeightsMapW2.put("Q"+queryId, qscoreMap);
				normQscoreMap = normalise(qscoreMap);
			//}
			finalScore = 0.0;
			for(int i = 0; i < normScoreMap.length; i++) {
				finalScore += (normScoreMap[i] * normQscoreMap[i]);
			}
			cosineScoreW2[iDoc++][queryId - 1] = finalScore;
		}
	}

	private static double[] normalise(TreeMap<String, Double> scoreMap) {
		double wt = 0.0, sumOfSqrs = 0.0;
		double[] normVector = new double[scoreMap.size()];
		for (String term : scoreMap.keySet()) {
			wt = scoreMap.get(term);
			sumOfSqrs += (wt * wt);
		}
		sumOfSqrs = Math.sqrt(sumOfSqrs);
		int i = 0;
		for (String term : scoreMap.keySet()) {
			wt = scoreMap.get(term);
			
			//if (wt == 0 && sumOfSqrs == 0)
				//System.out.println(wt);
				//normVector[i++] = 0.0;
			//else
				normVector[i++] = wt/sumOfSqrs;
		}
		return normVector;
	}

	//nlp lemmatizer from the jar, set up for API
	public static String lemmatize(String documentText)
    {
        List<String> lemmas = new LinkedList<String>();
        // Create an empty Annotation just with the given text
        Annotation document = new Annotation(documentText);
        // run all Annotators on this text
        pipeline.annotate(document);
        // Iterate over all of the sentences found
        String temp="";
        List<CoreMap> sentences = document.get(SentencesAnnotation.class);
        for(CoreMap sentence: sentences) {
            // Iterate over all tokens in a sentence
            for (CoreLabel token: sentence.get(TokensAnnotation.class)) {
            	temp = token.get(LemmaAnnotation.class);
            	lemmas.add(temp);
            }
        }
        return lemmas.get(0).toString();
    }
	
	public static void StanfordLemmatizer() {
        Properties props;
        props = new Properties();
        props.put("annotators", "tokenize, ssplit, pos, lemma");
        pipeline = new StanfordCoreNLP(props);
    }
}
