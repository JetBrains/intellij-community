// The logic is taken from https://github.com/mjpost/sacrebleu/blob/master/sacrebleu/metrics/bleu.py, simplified by eliminating
// smoothing methods and advanced settings for clarity in core BLEU calculation.
// For simplifying, a single prediction and reference text is assumed in the BLEU score calculation.
package com.intellij.cce.metric.util

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.min

fun computeBleuScore(candidateText: String, referenceText: String): Double {
  val candidateTokens = tokenizeText(candidateText)
  val referenceTokens = tokenizeText(referenceText)

  val maxN = 4
  val candidateLength = candidateTokens.size
  val effectiveOrder = minOf(maxN, candidateLength)
  val weights = List(effectiveOrder) { 1.0 / effectiveOrder }

  var logScore = 0.0
  var allPrecisionsPositive = true

  for (n in 1..effectiveOrder) {
    val candNgrams = getNGrams(candidateTokens, n)
    val refNgrams = getNGrams(referenceTokens, n)

    val candCounts = candNgrams.groupingBy { it }.eachCount()
    val refCounts = refNgrams.groupingBy { it }.eachCount()

    var overlap = 0
    var total = 0
    for ((ngram, count) in candCounts) {
      val refCount = refCounts.getOrDefault(ngram, 0)
      overlap += min(count, refCount)
      total += count
    }

    val precision = if (total > 0) overlap.toDouble() / total else 0.0

    if (precision > 0) {
      logScore += weights[n - 1] * ln(precision)
    } else {
      logScore += weights[n - 1] * Double.NEGATIVE_INFINITY
      allPrecisionsPositive = false
      break
    }
  }

  val brevityPenalty = calculateBrevityPenalty(referenceTokens.size, candidateTokens.size)
  val bleuScore = brevityPenalty * exp(logScore)
  return if (!allPrecisionsPositive || bleuScore.isNaN() || bleuScore.isInfinite()) 0.0 else bleuScore
}

fun tokenizeText(text: String): List<String> {
  val regex = Regex("""\p{L}+|\p{N}+|[^\s\p{L}\p{N}']+""")
  return regex.findAll(text).map { it.value.lowercase() }.toList()
}

fun getNGrams(words: List<String>, n: Int): List<String> {
  return if (words.size < n) emptyList() else words.windowed(n).map { it.joinToString(" ") }
}

fun calculateBrevityPenalty(refLength: Int, candLength: Int): Double {
  return if (candLength > refLength) 1.0 else exp(1.0 - refLength.toDouble() / candLength)
}

