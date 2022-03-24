package com.intellij.cce.metric

import com.intellij.cce.core.Session

interface Metric {
  fun evaluate(sessions: List<Session>, comparator: SuggestionsComparator = SuggestionsComparator.DEFAULT): Number
  val value: Double
  val name: String
  val valueType: MetricValueType
}
