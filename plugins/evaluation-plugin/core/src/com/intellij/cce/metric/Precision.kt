// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.metric

import com.intellij.cce.core.Session
import com.intellij.cce.metric.util.Bootstrap
import com.intellij.cce.metric.util.Sample

class Precision : Metric {
  private val sample = mutableListOf<Double>()
  override val name = "Precision"
  override val description: String = "Ratio of selected proposals by all proposals"
  override val showByDefault: Boolean = true
  override val valueType = MetricValueType.DOUBLE
  override val value: Double
    get() = sample.average()

  override fun confidenceInterval(): Pair<Double, Double> = Bootstrap.computeInterval(sample) { it.average() }

  override fun evaluate(sessions: List<Session>): Double {
    val lookups = sessions.flatMap { session -> session.lookups }
    val fileSample = Sample()
    lookups.forEach { lookup ->
      for (suggestion in lookup.suggestions) {
        if (suggestion.isRelevant) {
          fileSample.add(1.0)
          sample.add(1.0)
        }
        else {
          fileSample.add(0.0)
          sample.add(0.0)
        }
      }
    }
    return fileSample.mean()
  }
}