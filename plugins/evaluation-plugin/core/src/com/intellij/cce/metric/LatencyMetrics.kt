package com.intellij.cce.metric

import com.intellij.cce.core.Lookup
import com.intellij.cce.core.Session
import com.intellij.cce.metric.util.Bootstrap

abstract class LatencyMetric(override val name: String) : Metric {
  private val sample = mutableListOf<Double>()
  override val value: Double
    get() = compute(sample)

  override fun confidenceInterval(): Pair<Double, Double>? = Bootstrap.computeInterval(sample) { compute(it) }

  override fun evaluate(sessions: List<Session>, comparator: SuggestionsComparator): Double {
    val fileSample = mutableListOf<Double>()
    sessions
      .flatMap { session -> session.lookups }
      .filter(::shouldInclude)
      .forEach {
        this.sample.add(it.latency.toDouble())
        fileSample.add(it.latency.toDouble())
      }
    return compute(fileSample)
  }

  abstract fun compute(sample: List<Double>): Double

  open fun shouldInclude(lookup: Lookup) = true
}

class MaxLatencyMetric : LatencyMetric(NAME) {
  override val valueType = MetricValueType.INT

  override fun compute(sample: List<Double>): Double = sample.max()

  companion object {
    const val NAME = "Max Latency"
  }
}

class TotalLatencyMetric : LatencyMetric(NAME) {
  override val valueType = MetricValueType.DOUBLE
  override val showByDefault = false

  override fun compute(sample: List<Double>): Double = sample.sum()

  companion object {
    const val NAME = "Total Latency"
  }
}

class MeanLatencyMetric(private val filterZeroes: Boolean = false) : LatencyMetric(NAME) {
  override val valueType = MetricValueType.DOUBLE

  override fun compute(sample: List<Double>): Double = sample.average()

  override fun shouldInclude(lookup: Lookup) = if (filterZeroes) lookup.latency > 0 else true

  companion object {
    const val NAME = "Mean Latency"
  }
}
