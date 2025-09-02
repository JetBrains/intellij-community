package com.intellij.cce.metric

import com.intellij.cce.core.Lookup
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

  enum class RelaxedResult(val weight: Double) { NO(0.0), SINGLE(1.0), MULTI(2.0) }

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
    val prefixMatch = missingCode.trim().startsWith(completion.trim())

    val matchingLines = completionLines.map { predicate(it, middleLines) }
    val hasFirstLineMatching = matchingLines.isNotEmpty() && matchingLines[0] == true
    val multilineMatch = matchingLines.all { it == true }

    return when {
      multilineMatch -> RelaxedResult.MULTI
      hasFirstLineMatching || prefixMatch -> RelaxedResult.SINGLE
      else -> RelaxedResult.NO
    }
  }

  interface RelaxedMetric {
    fun compute(middle: String, completion: String, prefix: String = "", suffix: String = "", stripChars: Boolean = false): RelaxedResult
  }

  class RelaxedExactMatch : RelaxedMetric {
    override fun compute(middle: String, completion: String, prefix: String, suffix: String, stripChars: Boolean): RelaxedResult {
      return computeRelaxedSimilarity(middle, completion, prefix, suffix, stripChars) { line, middleLines ->
        line in middleLines
      }
    }
  }

  class RelaxedEditDistance(private val threshold: Double = 0.7) : RelaxedMetric {
    private fun normalizedEditDistance(left: String, right: String): Double {
      val norm = listOf(left, right).maxOf { it.length }
      val result = LevenshteinDistance(norm).apply(left, right).toDouble() / norm
      return result
    }

    override fun compute(middle: String, completion: String, prefix: String, suffix: String, stripChars: Boolean): RelaxedResult {
      return computeRelaxedSimilarity(middle, completion, prefix, suffix, stripChars) { line, middleLines ->
        middleLines.any { 1 - normalizedEditDistance(line, it) > threshold }
      }
    }
  }
}

abstract class BaseRelaxedMetric(showByDefault: Boolean) : LineSimilarityMetric(showByDefault) {
  abstract val onlyValuable: Boolean
  abstract val metric: RelaxedSimilarityUtils.RelaxedMetric

  override fun computeSimilarity(lookup: Lookup, expectedText: String): Double? {
    val completion = lookup.getWithPrefix() ?: return null
    val match = metric.compute(middle = expectedText, completion = completion, stripChars = onlyValuable)
    return match.weight
  }

  override fun computeExpected(lookup: Lookup, expectedText: String): Double = RelaxedSimilarityUtils.RelaxedResult.MULTI.weight
}

class RelaxedExactMatchOnlyAlphanum(showByDefault: Boolean = false) : BaseRelaxedMetric(showByDefault) {
  override val name: String = "Relaxed alphanumeric-only exact match"
  override val description: String =
    "Checks that for any the suggested lines of the completion, at least one of the lines from middle matches it."
  override val onlyValuable: Boolean = true
  override val metric: RelaxedSimilarityUtils.RelaxedMetric = RelaxedSimilarityUtils.RelaxedExactMatch()
}

class RelaxedExactMatch(showByDefault: Boolean = false) : BaseRelaxedMetric(showByDefault) {
  override val name: String = "Relaxed exact match"
  override val description: String =
    "Checks that for any the suggested lines of the completion, there is a line from middle that matches it."
  override val onlyValuable: Boolean = false
  override val metric: RelaxedSimilarityUtils.RelaxedMetric = RelaxedSimilarityUtils.RelaxedExactMatch()
}

/**
 * Note that the default threshold value is picked experimentally.
 */
private const val BEST_EDIT_ALPHANUM_THRESHOLD = 0.7674
private const val BEST_EDIT_THRESHOLD = 0.7412

class RelaxedEditDistanceOnlyAlphanum(
  showByDefault: Boolean = false,
  threshold: Double = BEST_EDIT_ALPHANUM_THRESHOLD,
) : BaseRelaxedMetric(showByDefault) {
  override val name: String = "Relaxed alphanumeric-only edit distance"
  override val description: String =
    "Checks that for any the suggested lines of the completion, there is a line from middle that has a normalized edit distance less than $threshold. (alphanumeric-only)"
  override val onlyValuable: Boolean = true
  override val metric: RelaxedSimilarityUtils.RelaxedMetric = RelaxedSimilarityUtils.RelaxedEditDistance(threshold)
}

class RelaxedEditDistance(
  showByDefault: Boolean = false,
  threshold: Double = BEST_EDIT_THRESHOLD,
) : BaseRelaxedMetric(showByDefault) {
  override val name: String = "Relaxed edit distance"
  override val description: String =
    "Checks that for any the suggested lines of the completion, there is a line from middle that has a normalized edit distance larger than $threshold."
  override val onlyValuable: Boolean = false
  override val metric: RelaxedSimilarityUtils.RelaxedMetric = RelaxedSimilarityUtils.RelaxedEditDistance(threshold)
}
