// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.metric

import com.intellij.cce.core.Session
import com.intellij.cce.metric.util.Sample

class LookupsCountMetric(override val name: String = "Lookups"): Metric {
  private val sample = Sample()
  override val description: String = "Number of lookups"
  override val showByDefault: Boolean = true
  override val valueType = MetricValueType.INT
  override val value: Double
    get() = sample.sum()

  override fun evaluate(sessions: List<Session>): Double {
    val lookupsCount = sessions.sumOf { it.lookups.size.toDouble() }
    sample.add(lookupsCount)
    return lookupsCount
  }
}
