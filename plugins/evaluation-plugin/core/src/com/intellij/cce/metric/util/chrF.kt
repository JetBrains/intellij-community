package com.intellij.cce.metric.util

import kotlin.math.min

// This implementation is based on https://github.com/m-popovic/chrF

private val PUNCTUATION = setOf(
  '!', '"', '#', '$', '%', '&', '\'', '(', ')', '*', '+', '-', '.', ',', '/', ':',
  ';', '<', '=', '>', '?', '@', '[', '\\', ']', '^', '_', '`', '{', '|', '}', '~',
)
private val EPSILON = 1e-16

private fun separateCharacters(line: String) = line
  .trim()
  .filter { it != ' ' }
  .map { it.toString() }

private fun separatePunctuation(line: String): List<String> {
  val words = line
    .trim()
    .split(" ")
    .map { it.trim() }
    .filterNot { it.isEmpty() }
  val tokenized = mutableListOf<String>()
  for (w in words) {
    if (w.length == 1) {
      tokenized.add(w)
    } else {
      val lastChar = w.last()
      val firstChar = w.first()
      if (lastChar in PUNCTUATION) {
        tokenized.add(w.dropLast(1))
        tokenized.add(lastChar.toString())
      }
      else if (firstChar in PUNCTUATION) {
        tokenized.add(firstChar.toString())
        tokenized.add(w.drop(1))
      } else {
        tokenized.add(w)
      }
    }
  }
  return tokenized
}

typealias NGramCounts = HashMap<Int, HashMap<List<String>, Double>>
typealias NGramMatchResult = Triple<Map<Int, Double>, Map<Int, Double>, Map<Int, Double>>

private data class NGramResult(
  val fscore: Map<Int, Double>,
  val precision: Map<Int, Double>,
  val recall: Map<Int, Double>,
)
data class CharFResult(val fscore: Double, val precision: Double, val recall: Double)

private fun NGramCounts.sum() = mapValues { it.value.values.sum() }
private fun Map<Int, Double>.sum() = values.sum()
private fun Map<Int, Double>.getOrZero(key: Int) = getOrElse(key) { 0.0 }

private fun ngramCounts(wordList: List<String>, order: Int): NGramCounts {
  val counts = hashMapOf<Int, HashMap<List<String>, Double>>()
  val nWords = wordList.size
  for (i in 0 until nWords) {
    for (j in 1..order) {
      if (i + j <= nWords) {
        val ngram = wordList.subList(i, i + j)
        val index = j - 1
        val currentCount = counts[index]?.get(ngram) ?: 0.0
        val updatedCount = currentCount + 1.0

        counts.getOrPut(index) { hashMapOf() }[ngram] = updatedCount
      }
    }
  }
  return counts
}

private fun ngramMatches(referenceNgrams: NGramCounts, hypothesisNgrams: NGramCounts): NGramMatchResult {
  val matchingNgramCount = mutableMapOf<Int, Double>()
  val totalRefNgramCount = mutableMapOf<Int, Double>()

  for ((order, refNgram) in referenceNgrams) {
    totalRefNgramCount[order] = referenceNgrams[order]?.values?.sum() ?: 0.0
    refNgram.forEach { (ngram, count) ->
      if (ngram in (hypothesisNgrams[order] ?: emptyMap())) {
        val oldValue = matchingNgramCount.getOrPut(order) { 0.0 }
        val increment = min(count, hypothesisNgrams[order]?.getOrDefault(ngram, 0.0) ?: 0.0)
        matchingNgramCount[order] = oldValue + increment
      }
    }
  }
  return Triple(matchingNgramCount, totalRefNgramCount, hypothesisNgrams.sum())
}

private fun ngramFScorePrecisionRecall(
  matching: Map<Int, Double>,
  referenceCount: Map<Int, Double>,
  hypothesisCount: Map<Int, Double>,
  beta: Double,
): NGramResult {
  val ngramPrecision = mutableMapOf<Int, Double>()
  val ngramRecall = mutableMapOf<Int, Double>()
  val ngramF = mutableMapOf<Int, Double>()
  val factor = beta * beta

  for ((order, match) in matching) {
    val precisionNormalizer = hypothesisCount.getOrZero(order)
    val recallNormalizer = referenceCount.getOrZero(order)
    ngramPrecision[order] = if (precisionNormalizer > 0.0) { match / precisionNormalizer } else { EPSILON }
    ngramRecall[order] = if (recallNormalizer > 0.0) match / recallNormalizer else EPSILON

    val precision = ngramPrecision.getOrZero(order)
    val recall = ngramRecall.getOrZero(order)
    val denom = factor * precision + recall
    val f = if (denom > 0) { (1 + factor) * precision * recall / denom } else { EPSILON }
    ngramF[order] = f
  }

  return NGramResult(ngramF, ngramRecall, ngramPrecision)
}

fun chrF(
  reference: String,
  hypothesis: String,
  nworder: Int = 2,
  ncorder: Int = 6,
  beta: Double = 2.0,
): CharFResult {
  val norder = nworder + ncorder

  val (matchingNgramCounts, totalRefNgramCount, totalHypNgramCount) = ngramMatches(
    ngramCounts(separatePunctuation(reference), nworder),
    ngramCounts(separatePunctuation(hypothesis), nworder),
  )
  val (matchingChrNgramCounts, totalChrRefNgramCount, totalChrHypNgramCount) = ngramMatches(
    ngramCounts(separateCharacters(reference), ncorder),
    ngramCounts(separateCharacters(hypothesis), ncorder),
  )

  val nGramScore = ngramFScorePrecisionRecall(matchingNgramCounts, totalRefNgramCount, totalHypNgramCount, beta)
  val charNGramScore = ngramFScorePrecisionRecall(matchingChrNgramCounts, totalChrRefNgramCount, totalChrHypNgramCount, beta)

  return CharFResult(
    fscore = (charNGramScore.fscore.sum() + nGramScore.fscore.sum()) / norder,
    precision = (charNGramScore.precision.sum() + nGramScore.precision.sum()) / norder,
    recall = (charNGramScore.recall.sum() + nGramScore.recall.sum()) / norder
  )
}
