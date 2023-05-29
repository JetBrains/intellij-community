package com.intellij.cce.metric

import com.intellij.cce.core.Session

interface Metric {
  fun evaluate(sessions: List<Session>, comparator: SuggestionsComparator = SuggestionsComparator.DEFAULT): Number

  fun confidenceInterval(): Pair<Double, Double>? = null

  val value: Double

  val name: String

  val valueType: MetricValueType

  val showByDefault: Boolean
    get() = true
}
