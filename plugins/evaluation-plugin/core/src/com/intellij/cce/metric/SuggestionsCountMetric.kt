package com.intellij.cce.metric

import com.intellij.cce.core.Session
import com.intellij.cce.metric.util.Sample

class SuggestionsCountMetric : Metric {
  private val sample = Sample()
  override val name = NAME
  override val valueType = MetricValueType.INT
  override val value: Double
    get() = sample.sum()

  override fun evaluate(sessions: List<Session>, comparator: SuggestionsComparator): Double {
    val suggestions = sessions.flatMap { it.lookups }.sumOf { it.suggestions.size }.toDouble()
    sample.add(suggestions)
    return suggestions
  }

  override val showByDefault = false

  companion object {
    const val NAME = "Suggestions"
  }
}
