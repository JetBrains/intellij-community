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

  open fun shouldInclude(lookup: Lookup): Boolean = true
}

class MaxLatencyMetric : LatencyMetric("Max Latency") {
  override fun compute(sample: Sample): Double = sample.max()

  override val valueType = MetricValueType.INT
}

class MeanLatencyMetric(private val filterZeroes: Boolean = false) : LatencyMetric("Mean Latency") {
  override fun compute(sample: Sample): Double = sample.mean()

  override val valueType = MetricValueType.DOUBLE

  override fun shouldInclude(lookup: Lookup): Boolean {
    return if (filterZeroes) lookup.latency > 0 else true
  }
}