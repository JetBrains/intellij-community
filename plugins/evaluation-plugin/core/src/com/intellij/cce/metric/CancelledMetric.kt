// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.metric

import com.intellij.cce.core.Session
import com.intellij.cce.metric.util.Sample

class CancelledMetric (override val showByDefault: Boolean): Metric {
  private val sample = Sample()
  override val name = NAME
  override val description: String = "Ratio of non-empty sessions with no matching suggestions"
  override val valueType = MetricValueType.DOUBLE
  override val value: Double
    get() = sample.mean()

  override fun evaluate(sessions: List<Session>): Double {
    val lookups = sessions.flatMap { session -> session.lookups }

    val fileSample = Sample()
    lookups.forEach { lookup ->
      val value = if (lookup.suggestions.isEmpty() || lookup.suggestions.any { it.isRelevant }) 0.0 else 1.0
      fileSample.add(value)
      sample.add(value)
    }
    return fileSample.mean()
  }

  companion object {
    const val NAME = "Cancelled"
  }
}
