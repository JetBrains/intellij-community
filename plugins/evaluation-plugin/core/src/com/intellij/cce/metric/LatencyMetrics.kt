// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.metric

import com.intellij.cce.core.Lookup
import com.intellij.cce.core.Session

abstract class LatencyMetric(override val name: String) : ConfidenceIntervalMetric<Double>() {
  override val value: Double
    get() = compute(sample)

  override fun evaluate(sessions: List<Session>): Double {
    val fileSample = mutableListOf<Double>()
    sessions
      .flatMap { session -> session.lookups }
      .filter(::shouldInclude)
      .forEach {
        this.coreSample.add(it.latency.toDouble())
        fileSample.add(it.latency.toDouble())
      }
    return compute(fileSample)
  }

  abstract override fun compute(sample: List<Double>): Double

  open fun shouldInclude(lookup: Lookup) = true
}

class MaxLatencyMetric : LatencyMetric("Max Latency") {
  override val description: String = "Maximum invocation latency"
  override val valueType = MetricValueType.INT
  override val showByDefault: Boolean = false

  override fun compute(sample: List<Double>): Double = sample.maxOrNull() ?: Double.NaN
}

class TotalLatencyMetric : LatencyMetric("Total Latency") {
  override val description: String = "Sum of invocations latencies"
  override val valueType = MetricValueType.DOUBLE
  override val showByDefault = false

  override fun compute(sample: List<Double>): Double = sample.sum()
}

class MeanLatencyMetric(private val filterZeroes: Boolean = false) : LatencyMetric("Mean Latency") {
  override val valueType = MetricValueType.DOUBLE
  override val description: String = "Average latency by all invocations"
  override val showByDefault: Boolean = true

  override fun compute(sample: List<Double>): Double = sample.average()

  override fun shouldInclude(lookup: Lookup) = !filterZeroes || lookup.latency > 0
}

class SuccessMeanLatencyMetric(private val filterZeroes: Boolean = false) : LatencyMetric("Mean Success Latency") {
  override val valueType = MetricValueType.DOUBLE
  override val description: String = "Average latency by invocations with selected proposal"
  override val showByDefault = false

  override fun compute(sample: List<Double>): Double = sample.average()

  override fun shouldInclude(lookup: Lookup) = lookup.selectedPosition >= 0 && (!filterZeroes || lookup.latency > 0)
}

class PercentileLatencyMetric(private val percentile: Int) : LatencyMetric("Latency Percentile $percentile") {
  override val valueType = MetricValueType.INT
  override val description: String = "Latency $percentile percentile by all invocations"
  override val showByDefault = false

  override val maximumSessions: Int
    get() = 10000

  override fun compute(sample: List<Double>): Double = computePercentile(sample, percentile)
}

class SuccessPercentileLatencyMetric(private val percentile: Int) : LatencyMetric("Latency Success Percentile $percentile") {
  override val valueType = MetricValueType.INT
  override val description: String = "Latency $percentile percentile by invocations with selected proposal"
  override val showByDefault = false

  override val maximumSessions: Int
    get() = 10000

  override fun compute(sample: List<Double>): Double = computePercentile(sample, percentile)

  override fun shouldInclude(lookup: Lookup): Boolean = lookup.selectedPosition >= 0
}

private fun computePercentile(sample: List<Double>, percentile: Int): Double {
  if (sample.isEmpty()) return Double.NaN
  require(percentile in 0..100) { "Percentile must be between 0 and 100" }
  val index = (sample.size * percentile / 100).coerceAtMost(sample.size - 1)
  return sample.sorted()[index]
}
