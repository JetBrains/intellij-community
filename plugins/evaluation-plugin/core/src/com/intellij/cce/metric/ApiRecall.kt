package com.intellij.cce.metric

import com.intellij.cce.core.Lookup
import com.intellij.cce.core.Session
import com.intellij.cce.evaluable.AIA_GROUND_TRUTH_INTERNAL_API_CALLS
import com.intellij.cce.evaluable.AIA_PREDICTED_API_CALLS
import com.intellij.cce.metric.util.Sample

abstract class ApiRecall : ConfidenceIntervalMetric<Double>() {
  final override val showByDefault: Boolean = true
  final override val valueType = MetricValueType.DOUBLE
  final override val value: Double
    get() = compute(sample)

  override val supportsIndividualScores: Boolean = true

  abstract fun extractPredictedApiCallsFromLookup(lookup: Lookup): List<String>
  abstract fun extractExpectedApiCallsFromLookup(lookup: Lookup): List<String>

  @Suppress("UNCHECKED_CAST")
  final override fun evaluate(sessions: List<Session>): Number {
    val fileSample = Sample()
    sessions
      .flatMap { it.lookups }
      .forEach {
        val predictedApiCalls = extractPredictedApiCallsFromLookup(it)
        val groundTruthApiCalls = extractExpectedApiCallsFromLookup(it)
        val apiRecall = calculateApiRecallForLookupSnippets(predictedApiCalls, groundTruthApiCalls)
        fileSample.add(apiRecall)
        coreSample.add(apiRecall)
      }
    return fileSample.mean()
  }

  final override fun compute(sample: List<Double>): Double = sample.average()

  private fun calculateApiRecallForLookupSnippets(
    predictedApiCalls: List<String>,
    groundTruthApiCalls: List<String>,
  ): Double {
    if (groundTruthApiCalls.isEmpty()) return 1.0

    val uniqueGroundTruthApiCalls = groundTruthApiCalls.toSet()
    val uniquePredictedApiCalls = predictedApiCalls.toSet()
    val intersection = uniquePredictedApiCalls.intersect(uniqueGroundTruthApiCalls)
    return intersection.size.toDouble() / uniqueGroundTruthApiCalls.size.toDouble()
  }
}

class InternalApiRecall : ApiRecall() {
  override val name: String = "API Recall"
  override val description: String = "The fraction of correctly guessed project-defined API calls"

  override fun extractPredictedApiCallsFromLookup(lookup: Lookup): List<String> {
    return lookup.additionalList(AIA_PREDICTED_API_CALLS) ?: emptyList()
  }

  override fun extractExpectedApiCallsFromLookup(lookup: Lookup): List<String> {
    return lookup.additionalList(AIA_GROUND_TRUTH_INTERNAL_API_CALLS) ?: emptyList()
  }
}

class ExternalApiRecall : ApiRecall() {
  override val name: String = "External API Recall"
  override val description: String = "The fraction of correctly guessed library-defined API calls"

  companion object {
    const val AIA_PREDICTED_EXTERNAL_API_CALLS = "external_api_calls"
    const val AIA_GROUND_TRUTH_EXTERNAL_API_CALLS = "external_api_calls_gt"
  }

  override fun extractPredictedApiCallsFromLookup(lookup: Lookup): List<String> {
    return lookup.additionalList(AIA_PREDICTED_EXTERNAL_API_CALLS) ?: emptyList()
  }

  override fun extractExpectedApiCallsFromLookup(lookup: Lookup): List<String> {
    return lookup.additionalList(AIA_GROUND_TRUTH_EXTERNAL_API_CALLS) ?: emptyList()
  }
}

internal fun Lookup.additionalList(key: String): List<String>? =
  additionalInfo[key]?.let { it as String }?.split("\n")?.filter { it.isNotEmpty() }
