// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

  override fun compute(sample: List<Double>): Double = sample.average()

  override fun shouldInclude(lookup: Lookup) = if (filterZeroes) lookup.latency > 0 else true
}
