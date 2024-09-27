package com.intellij.cce.metric

import org.apache.commons.text.similarity.LevenshteinDistance

object RelaxedSimilarityUtils {
  private val nonValuable = Regex("[^a-zA-Z0-9_+\\-*%=&|@\$?]")

  fun preProcessLines(completion: String, prefix: String, suffix: String, stripChars: Boolean = true): List<String> {
    val completionWithPrefix = buildString {
      if (!prefix.endsWith("\n") && !completion.startsWith("\n")) {
        append(prefix.substringAfterLast("\n"))
      }
      append(completion)
      if (!completion.endsWith("\n") && !suffix.startsWith("\n")) {
        append(suffix.substringBefore("\n"))
      }
    }

    return completionWithPrefix.lines()
      .map { if (stripChars) it.replace(nonValuable, "") else it }
      .filter { it.isNotBlank() }
  }

  enum class RelaxedResult(val weight: Int) { NO(0), ANY(1), MULTI(2) }

  fun computeRelaxedSimilarity(
    middle: String,
    completion: String,
    prefix: String,
    suffix: String,
    stripChars: Boolean = false,
    predicate: (completionLine: String, middleLines: Set<String>) -> Boolean,
  ): RelaxedResult {
    if (middle.isBlank() || completion.isBlank()) return RelaxedResult.NO

    val missingCode = middle + suffix
    val completionLines = preProcessLines(completion, prefix, suffix, stripChars)
    val middleLines = preProcessLines(middle, prefix, suffix, stripChars).toSet()
    val prefixMatch = missingCode.startsWith(completion.trim())

    val matchingLines = completionLines.count { predicate(it, middleLines) }
    val hasMatchingLine = matchingLines > 0
    val multilineMatch = matchingLines == completionLines.size

    return when {
      multilineMatch -> RelaxedResult.MULTI
      hasMatchingLine || prefixMatch -> RelaxedResult.ANY
      else -> RelaxedResult.NO
    }
  }

  fun computeRelaxedExactMatch(
    middle: String,
    completion: String,
    prefix: String,
    suffix: String,
    stripChars: Boolean = false,
  ): RelaxedResult = computeRelaxedSimilarity(middle, completion, prefix, suffix, stripChars) {
    line, middleLines -> line in middleLines
  }

  private fun normalizedEditDistance(left: String, right: String): Double {
    val norm = listOf(left, right).maxOf { it.length }
    return LevenshteinDistance(norm).apply(left, right).toDouble() / norm
  }

  fun computeRelaxedEditDistance(
    middle: String,
    completion: String,
    prefix: String,
    suffix: String,
    stripChars: Boolean,
    threshold: Double = 0.5,
  ): RelaxedResult = computeRelaxedSimilarity(middle, completion, prefix, suffix, stripChars) {
    line, middleLines -> 1 - normalizedEditDistance(line, middle) > threshold
  }
}
