// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.metric

import com.intellij.cce.core.Session
import com.intellij.cce.metric.util.Sample

class CancelledAtMetric (override val showByDefault: Boolean, private val n: Int) : Metric {
  private val sample = Sample()
  override val name = NAME + n
  override val description: String = "Ratio of non-empty sessions with no matching suggestions at top-$n"
  override val valueType = MetricValueType.DOUBLE
  override val value: Double
    get() = sample.mean()

  override fun evaluate(sessions: List<Session>): Double {
    val lookups = sessions.flatMap { session -> session.lookups }

    val fileSample = Sample()
    lookups.forEach { lookup ->
      val indexOfRelevantSuggestion = lookup.suggestions.indexOfFirst { it.isRelevant }
      if (lookup.suggestions.isEmpty() || indexOfRelevantSuggestion in 0 until n) {
        fileSample.add(0.0)
        sample.add(0.0)
      }
      else {
        fileSample.add(1.0)
        sample.add(1.0)
      }
    }
    return fileSample.mean()
  }

  companion object {
    const val NAME = "CancelledAt"
  }
}
