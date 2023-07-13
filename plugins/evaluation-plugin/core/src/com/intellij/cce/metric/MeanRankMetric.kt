// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.metric

import com.intellij.cce.core.Session
import com.intellij.cce.metric.util.Sample

class MeanRankMetric : Metric {
  private val sample = Sample()
  override val name = "Mean Rank"
  override val description: String = "Avg position of selected proposal by invocations"
  override val valueType = MetricValueType.DOUBLE
  override val showByDefault: Boolean = false
  override val value: Double
    get() = sample.mean()

  override fun evaluate(sessions: List<Session>, comparator: SuggestionsComparator): Double {
    val completions = sessions.map { session -> Pair(session.lookups.last().suggestions, session.expectedText) }

    val fileSample = Sample()
    completions.forEach { (suggests, expectedText) ->
      val position = suggests.indexOfFirst { comparator.accept(it, expectedText) }
      if (position != -1) {
        fileSample.add(position.toDouble())
        sample.add(position.toDouble())
      }
    }

    return fileSample.mean()
  }
}
