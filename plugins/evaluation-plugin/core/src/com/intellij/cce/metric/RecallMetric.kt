// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.metric

import com.intellij.cce.core.Session
import com.intellij.cce.metric.util.Sample

class RecallMetric : Metric {
  private val sample = Sample()
  override val name = NAME
  override val description: String = "Ratio of successful invocations"
  override val valueType = MetricValueType.DOUBLE
  override val value: Double
    get() = sample.mean()

  override fun evaluate(sessions: List<Session>, comparator: SuggestionsComparator): Double {
    val listOfCompletions = sessions
      .flatMap { session -> session.lookups.map { lookup -> Pair(lookup.suggestions, session.expectedText) } }

    val fileSample = Sample()
    listOfCompletions
      .forEach { (suggests, expectedText) ->
        val value = if (suggests.any { comparator.accept(it, expectedText) }) 1.0 else 0.0
        fileSample.add(value)
        sample.add(value)
      }

    return fileSample.mean()
  }

  companion object {
    const val NAME = "Recall"
  }
}
