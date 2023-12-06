package com.intellij.cce.metric

import com.intellij.cce.core.Session
import com.intellij.cce.metric.util.Sample

class RecallAtMetric (override val showByDefault: Boolean, private val n: Int) : Metric {
  private val sample = Sample()
  override val name = NAME_PREFIX + n
  override val description: String = "Ratio of invocations with matching proposal in top-$n"
  override val valueType = MetricValueType.DOUBLE
  override val value: Double
    get() = sample.mean()

  override fun evaluate(sessions: List<Session>): Double {
    val lookups = sessions.flatMap { session -> session.lookups }

    val fileSample = Sample()
    lookups.forEach { lookup ->
      val indexOfRelevantSuggestion = lookup.suggestions.indexOfFirst { it.isRelevant }
      if (indexOfRelevantSuggestion in 0 until n) {
        fileSample.add(1.0)
        sample.add(1.0)
      }
      else {
        fileSample.add(0.0)
        sample.add(0.0)
      }
    }

    return fileSample.mean()
  }

  companion object {
    const val NAME_PREFIX = "RecallAt"
  }
}
