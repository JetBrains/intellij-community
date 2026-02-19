package com.intellij.cce.metric

import com.intellij.cce.core.Lookup
import com.intellij.cce.core.Session
import com.intellij.cce.metric.util.chrF
import org.apache.commons.text.similarity.LevenshteinDistance


abstract class LineSimilarityMetric(showByDefault: Boolean) : SimilarityMetric(showByDefault) {
  protected fun Lookup.getWithPrefix() = suggestions.firstOrNull()?.text

  override fun computeExpectedText(session: Session, lookup: Lookup) = session.expectedText
}

class EditDistanceFirstLine(showByDefault: Boolean) : LineSimilarityMetric(showByDefault) {
  override val name = "Edit Distance First Line"
  override val description: String = "The minimum normalized edit distance to expected"

  override fun computeSimilarity(lookup: Lookup, expectedText: String): Double {
    val firstSuggestionLine = lookup.getWithPrefix()?.firstLine() ?: ""
    val firstExpectedLine = expectedText.firstLine()
    val distance = LevenshteinDistance.getDefaultInstance().apply(firstExpectedLine, firstSuggestionLine)
    return (firstExpectedLine.length - distance).toDouble().coerceAtLeast(0.0)
  }

  override fun computeExpected(lookup: Lookup, expectedText: String) = expectedText.firstLine().length.toDouble()
}

class NonEmptySuggestionRate(showByDefault: Boolean) : LineSimilarityMetric(showByDefault) {
  override val name = "Non-Empty Suggestion Rate"
  override val description = "Average session count that the suggestion was non-empty"

  override fun computeSimilarity(lookup: Lookup, expectedText: String): Double {
    val suggestion = lookup.getWithPrefix()
    return if (suggestion.isNullOrEmpty()) 0.0 else 1.0
  }

  override fun computeExpected(lookup: Lookup, expectedText: String) = 1.0
}

class PerfectFirstLine(showByDefault: Boolean) : LineSimilarityMetric(showByDefault) {
  override val name = "Perfect First Line"
  override val description: String = "Ratio of completions with proposal matches until the end of the first line"

  override fun computeSimilarity(lookup: Lookup, expectedText: String): Double {
    val firstSuggestionLine = lookup.getWithPrefix()?.firstLine()
    val firstExpectedLine = expectedText.firstLine()
    return if (firstSuggestionLine == firstExpectedLine) 1.0 else 0.0
  }

  override fun computeExpected(lookup: Lookup, expectedText: String): Double = 1.0
}

class CharFScoreFirstLine(showByDefault: Boolean = false) : LineSimilarityMetric(showByDefault) {
  override val name = "Character n-gram F-score First Line"
  override val description: String = "Average ChrF++ score on the first line"

  override fun computeSimilarity(lookup: Lookup, expectedText: String): Double {
    val firstSuggestionLine = lookup.getWithPrefix()?.firstLine()
    val firstExpectedLine = expectedText.firstLine()
    if (firstSuggestionLine.isNullOrEmpty()) return 0.0
    return chrF(firstExpectedLine, firstSuggestionLine).fscore
  }

  override fun computeExpected(lookup: Lookup, expectedText: String): Double = 1.0
}

class CharFScore(showByDefault: Boolean = false) : LineSimilarityMetric(showByDefault) {
  override val name = "Character n-gram F-score"
  override val description: String = "Average ChrF++ score on the whole proposal"

  override fun computeSimilarity(lookup: Lookup, expectedText: String): Double {
    val suggestion = lookup.getWithPrefix()
    if (suggestion.isNullOrEmpty()) return 0.0
    return chrF(expectedText, suggestion).fscore
  }

  override fun computeExpected(lookup: Lookup, expectedText: String): Double = 1.0
}

private fun String.firstLine() = lineSequence().first()
