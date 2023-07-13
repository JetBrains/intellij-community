// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.metric

import com.intellij.cce.core.Session
import com.intellij.cce.metric.util.Sample

class RecallAtMetric(private val n: Int) : Metric {
  private val sample = Sample()
  override val name = NAME_PREFIX + n
  override val description: String = "Ratio of invocations with selected proposal in top-$n"
  override val valueType = MetricValueType.DOUBLE
  override val value: Double
    get() = sample.mean()

  override fun evaluate(sessions: List<Session>, comparator: SuggestionsComparator): Double {
    val listOfCompletions = sessions
      .flatMap { session -> session.lookups.map { lookup -> Pair(lookup.suggestions, session.expectedText) } }

    val fileSample = Sample()
    for (completion in listOfCompletions) {
      val indexOfNecessaryCompletion = completion.first.indexOfFirst { comparator.accept(it, completion.second) }
      if (indexOfNecessaryCompletion in 0 until n) {
        fileSample.add(1.0)
        sample.add(1.0)
      }
      else {
        fileSample.add(0.0)
        sample.add(0.0)
      }
    }

    return fileSample.mean()
  }

  companion object {
    const val NAME_PREFIX = "RecallAt"
  }
}
