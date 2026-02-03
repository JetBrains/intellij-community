package com.intellij.cce.metric

import com.intellij.cce.core.Session
import com.intellij.cce.metric.util.Sample

class UnknownRelevanceRatio : Metric {
  override val name: String = "Unknown Relevance Ratio"
  override val description: String = "Ratio of suggestions in sessions with unknown relevance"
  override val valueType: MetricValueType = MetricValueType.DOUBLE
  override val showByDefault: Boolean = true

  private val sample = Sample()

  override fun evaluate(sessions: List<Session>): Number {
    val fileSample = Sample()
    sessions.forEach { session ->
      session.lookups.forEach { lookup ->
        lookup.suggestions.forEach { suggestion ->
          val value = if (isUnknown(suggestion.details)) 1.0 else 0.0
          sample.add(value)
          fileSample.add(value)
        }
      }
    }
    return fileSample.mean()
  }

  override val value: Double
    get() = sample.mean()

  companion object {
    private const val KEY = "unknown_relevance"
    fun isUnknown(details: Map<String, Any?>): Boolean = details[KEY] == true
    fun markUnknown(details: MutableMap<String, Any?>) {
      details[KEY] = true
    }
  }
}