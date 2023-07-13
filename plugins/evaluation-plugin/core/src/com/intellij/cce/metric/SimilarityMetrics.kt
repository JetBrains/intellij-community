// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.metric

import com.intellij.cce.core.Lookup
import com.intellij.cce.core.Session
import com.intellij.cce.core.SuggestionKind
import com.intellij.cce.metric.util.Bootstrap
import org.apache.commons.lang.StringUtils

abstract class SimilarityMetric(override val showByDefault: Boolean) : Metric {
  private var totalMatched: Double = 0.0
  private var totalExpected: Double = 0.0
  private var sample: MutableList<Pair<Double, Double>> = mutableListOf()

  override val valueType = MetricValueType.DOUBLE
  override val value: Double
    get() = totalMatched / totalExpected

  override fun confidenceInterval(): Pair<Double, Double> = Bootstrap.computeInterval(sample) { values ->
    values.sumOf { it.first } / values.sumOf { it.second }
  }

  override fun evaluate(sessions: List<Session>, comparator: SuggestionsComparator): Double {
    var matched = 0.0
    var expected = 0.0
    for (session in sessions) {
      for (lookup in session.lookups) {
        val expectedText = session.expectedText.substring(lookup.offset)
        val currentExpected = computeExpected(lookup, expectedText)
        expected += currentExpected
        val similarity = computeSimilarity(lookup, expectedText) ?: 0.0
        matched += similarity
        sample.add(Pair(similarity, currentExpected))
      }
    }
    totalMatched += matched
    totalExpected += expected
    return matched / expected
  }

  abstract fun computeSimilarity(lookup: Lookup, expectedText: String): Double?

  open fun computeExpected(lookup: Lookup, expectedText: String): Double = expectedText.length.toDouble()
}

class MatchedRatio(showByDefault: Boolean = false) : SimilarityMetric(showByDefault) {
  override val name = "Matched Ratio"
  override val description: String = "Length of selected proposal normalized by expected text (avg by invocations)"

  override fun computeSimilarity(lookup: Lookup, expectedText: String): Double? {
    if (lookup.selectedPosition == -1)
      return null
    val selected = lookup.suggestions[lookup.selectedPosition]
    if (selected.kind == SuggestionKind.TOKEN)
      return null
    return selected.text.length.toDouble() - lookup.prefix.length
  }
}

class PrefixSimilarity(showByDefault: Boolean = false) : SimilarityMetric(showByDefault) {
  override val name = "Prefix Similarity"
  override val description: String = "The most matching prefix among proposals normalized by expected text (avg by invocations)"

  override fun computeSimilarity(lookup: Lookup, expectedText: String): Double? =
    lookup.suggestions.maxOfOrNull {
      StringUtils.getCommonPrefix(arrayOf(it.text.drop(lookup.prefix.length), expectedText)).length
    }?.toDouble()
}

class EditSimilarity(showByDefault: Boolean = false) : SimilarityMetric(showByDefault) {
  override val name = "Edit Similarity"
  override val description: String = "The minimum edit similarity among proposals normalized by expected text (avg by invocations)"

  override fun computeSimilarity(lookup: Lookup, expectedText: String): Double? =
    lookup.suggestions.maxOfOrNull {
      expectedText.length - StringUtils.getLevenshteinDistance(it.text.drop(lookup.prefix.length), expectedText)
    }?.toDouble()
}
