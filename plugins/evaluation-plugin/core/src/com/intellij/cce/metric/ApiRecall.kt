package com.intellij.cce.metric

import com.intellij.cce.core.Lookup
import com.intellij.cce.core.Session
import com.intellij.cce.evaluable.AIA_GROUND_TRUTH_INTERNAL_API_CALLS
import com.intellij.cce.evaluable.AIA_PREDICTED_API_CALLS
import com.intellij.cce.metric.util.Bootstrap
import com.intellij.cce.metric.util.Sample

class ApiRecall : Metric {
  private val sample = mutableListOf<Double>()
  override val name: String = "API Recall"
  override val description: String = "The fraction of correctly guessed project-defined API calls"
  override val showByDefault: Boolean = true
  override val valueType = MetricValueType.DOUBLE
  override val value: Double
    get() = sample.average()

  @Suppress("UNCHECKED_CAST")
  override fun evaluate(sessions: List<Session>): Number {
    val fileSample = Sample()
    sessions
      .flatMap { it.lookups }
      .forEach {
        val predictedApiCalls = it.additionalList(AIA_PREDICTED_API_CALLS) ?: emptyList()
        val groundTruthApiCalls = it.additionalList(AIA_GROUND_TRUTH_INTERNAL_API_CALLS) ?: emptyList()
        val apiRecall = calculateApiRecallForLookupSnippets(predictedApiCalls, groundTruthApiCalls)
        fileSample.add(apiRecall)
        sample.add(apiRecall)
      }
    return fileSample.mean()
  }

  override fun confidenceInterval(): Pair<Double, Double> = Bootstrap.computeInterval(sample) { it.average() }

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

internal fun Lookup.additionalList(key: String): List<String>? =
  additionalInfo[key]?.let { it as String}?.split("\n")?.filter { it.isNotEmpty() }