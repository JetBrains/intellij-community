// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.metric

import com.intellij.cce.core.Lookup
import com.intellij.cce.core.Session
import com.intellij.cce.evaluable.AIA_RESPONSE
import com.intellij.cce.evaluable.AIA_USER_PROMPT
import com.intellij.cce.evaluable.REFERENCE_PROPERTY
import com.intellij.cce.metric.util.Bootstrap
import com.intellij.cce.metric.util.LLMJudge
import com.intellij.cce.metric.util.computeBleuScore
import org.apache.commons.lang3.StringUtils
import org.apache.commons.text.similarity.LevenshteinDistance
import kotlin.math.max
import kotlin.math.min

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

  override fun evaluate(sessions: List<Session>): Double {
    var matched = 0.0
    var expected = 0.0
    for (session in sessions) {
      for (lookup in session.lookups) {
        val expectedText = computeExpectedText(session, lookup)
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

  open fun computeExpectedText(session: Session, lookup: Lookup) = session.expectedText.substring(lookup.offset)

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
    return selected.text.length.toDouble() - lookup.prefix.length
  }
}

class MatchedRatioAt(showByDefault: Boolean = false, val n: Int) : SimilarityMetric(showByDefault) {
  override val name = "Matched Ratio At $n"
  override val description: String = "Length of the longest matching proposal among top-$n normalized by expected text (avg by invocations)"

  override fun computeSimilarity(lookup: Lookup, expectedText: String): Double {
    val numConsideredSuggestions = min(n, lookup.suggestions.size)
    var maxMatchedLen = 0
    for (i in 0 until numConsideredSuggestions) {
      if (lookup.suggestions[i].isRelevant) {
        val selected = lookup.suggestions[i]
        maxMatchedLen = max(maxMatchedLen, selected.text.length - lookup.prefix.length)
      }
    }
    return maxMatchedLen.toDouble()
  }
}

class MatchedRatioWithRelevanceModel(showByDefault: Boolean = false, private val relevance: String) : SimilarityMetric(showByDefault) {
  override val name = "Matched Ratio With ${relevance.capitalize()} Model"
  override val description: String = "Length of selected proposal normalized by expected text (avg by invocations) " +
                                     "taking $relevance model into account"

  override fun computeSimilarity(lookup: Lookup, expectedText: String): Double? {
    if (lookup.selectedPosition == -1 || lookup.additionalInfo["${relevance}_decision"] == "SKIP")
      return null
    val selected = lookup.suggestions[lookup.selectedPosition]
    return selected.text.length.toDouble() - lookup.prefix.length
  }
}

class PrefixSimilarity(showByDefault: Boolean = false) : SimilarityMetric(showByDefault) {
  override val name = "Prefix Similarity"
  override val description: String = "The most matching prefix among proposals normalized by expected text (avg by invocations)"

  override fun computeSimilarity(lookup: Lookup, expectedText: String): Double? =
    lookup.suggestions.maxOfOrNull {
      StringUtils.getCommonPrefix(it.text.drop(lookup.prefix.length), expectedText).length
    }?.toDouble()
}

class EditSimilarity(showByDefault: Boolean = false) : SimilarityMetric(showByDefault) {
  override val name = "Edit Similarity"
  override val description: String = "The minimum edit similarity among proposals normalized by expected text (avg by invocations)"

  override fun computeSimilarity(lookup: Lookup, expectedText: String): Double? {
    return lookup.suggestions.maxOfOrNull {
      expectedText.length - LevenshteinDistance.getDefaultInstance().apply(it.text.drop(lookup.prefix.length), expectedText)
    }?.toDouble()?.coerceAtLeast(0.0)
  }
}


class BleuScore(showByDefault: Boolean = true) : SimilarityMetric(showByDefault) {
  override val name = "BLEU Score"
  override val description: String = "Calculates the BLEU score for the AIA response compared to the reference text."

  override fun computeSimilarity(lookup: Lookup, expectedText: String): Double? {
    val aiaResponse = lookup.additionalInfo[AIA_RESPONSE] as? String ?: return null
    val reference = lookup.additionalInfo[REFERENCE_PROPERTY] as? String ?: return null
    val bleuScore = computeBleuScore(aiaResponse, reference)

    return bleuScore
  }

  override fun computeExpected(lookup: Lookup, expectedText: String): Double = 1.0
}

class LLMJudgeScore(showByDefault: Boolean = true,  private val llmJudge: LLMJudge) : SimilarityMetric(showByDefault) {
  override val name = "LLM Judge Score"
  override val description: String = "Calculates the LLM-as-a-Judge Score score for the AIA response compared to the reference text."

  override fun computeSimilarity(lookup: Lookup, expectedText: String): Double? {
    val aiaResponse = lookup.additionalInfo[AIA_RESPONSE] as? String ?: return null
    val reference = lookup.additionalInfo[REFERENCE_PROPERTY] as? String ?: return null
    val question = lookup.additionalInfo[AIA_USER_PROMPT] as? String ?: return null

    return llmJudge.computeLLMJudgeScoreSync(question, aiaResponse, reference)
  }

  override fun computeExpected(lookup: Lookup, expectedText: String): Double = 1.0
}
