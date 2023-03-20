package com.intellij.cce.metric

import com.intellij.cce.core.Lookup
import com.intellij.cce.core.Session
import com.intellij.cce.metric.util.Sample

abstract class LatencyMetric(override val name: String) : Metric {
  private val sample = Sample()
  override val value: Double
    get() = compute(sample)

  override fun evaluate(sessions: List<Session>, comparator: SuggestionsComparator): Double {
    val fileSample = Sample()
    sessions.stream()
      .flatMap { session -> session.lookups.stream() }
      .filter(::shouldInclude)
      .forEach {
        this.sample.add(it.latency.toDouble())
        fileSample.add(it.latency.toDouble())
      }
    return compute(fileSample)
  }

  abstract fun compute(sample: Sample): Double

  open fun shouldInclude(lookup: Lookup) = true
}

class MaxLatencyMetric : LatencyMetric(NAME) {
  override val valueType = MetricValueType.INT

  override fun compute(sample: Sample): Double = sample.max()

  companion object {
    const val NAME = "Max Latency"
  }
}

class TotalLatencyMetric : LatencyMetric(NAME) {
  override val valueType = MetricValueType.DOUBLE
  override val showByDefault = false

  override fun compute(sample: Sample): Double = sample.sum()

  companion object {
    const val NAME = "Total Latency"
  }
}

class MeanLatencyMetric(private val filterZeroes: Boolean = false) : LatencyMetric(NAME) {
  override val valueType = MetricValueType.DOUBLE

  override fun compute(sample: Sample): Double = sample.mean()

  override fun shouldInclude(lookup: Lookup) = if (filterZeroes) lookup.latency > 0 else true

  companion object {
    const val NAME = "Mean Latency"
  }
}
