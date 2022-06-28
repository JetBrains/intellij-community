package com.intellij.cce.metric

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
      .forEach {
        this.sample.add(it.latency.toDouble())
        fileSample.add(it.latency.toDouble())
      }
    return compute(fileSample)
  }

  abstract fun compute(sample: Sample): Double
}

class MaxLatencyMetric : LatencyMetric("Max Latency") {
  override fun compute(sample: Sample): Double = sample.max()

  override val valueType = MetricValueType.INT
}

class MeanLatencyMetric : LatencyMetric("Mean Latency") {
  override fun compute(sample: Sample): Double = sample.mean()

  override val valueType = MetricValueType.DOUBLE
}