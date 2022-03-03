package com.intellij.cce.metric

import com.intellij.cce.core.Session
import com.intellij.cce.metric.util.Sample

class SessionsCountMetric : Metric {
  private val sample = Sample()

  override val value: Double
    get() = sample.sum()

  override fun evaluate(sessions: List<Session>, comparator: SuggestionsComparator): Double {
    sample.add(sessions.size.toDouble())
    return sessions.size.toDouble()
  }

  override val name: String = "Sessions"

  override val valueType = MetricValueType.INT
}