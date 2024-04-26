// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.metric

import com.intellij.cce.core.Session
import com.intellij.cce.metric.util.Sample

class SuggestionsCountMetric : Metric {
  private val sample = Sample()
  override val name = "Suggestions"
  override val description: String = "Total number of proposals"
  override val showByDefault = false
  override val valueType = MetricValueType.INT
  override val value: Double
    get() = sample.sum()

  override fun evaluate(sessions: List<Session>): Double {
    val suggestions = sessions.flatMap { it.lookups }.sumOf { it.suggestions.size }.toDouble()
    sample.add(suggestions)
    return suggestions
  }
}
