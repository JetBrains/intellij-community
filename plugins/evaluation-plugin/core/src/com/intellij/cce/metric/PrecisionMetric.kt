// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.metric

import com.intellij.cce.core.Lookup
import com.intellij.cce.core.Session
import com.intellij.cce.metric.util.Sample

abstract class PrecisionMetricBase : ConfidenceIntervalMetric<Double>() {
  override val showByDefault: Boolean = true
  override val valueType = MetricValueType.DOUBLE
  override val value: Double
    get() = compute(sample)

  override fun compute(sample: List<Double>): Double = sample.average()

  override fun evaluate(sessions: List<Session>): Double {
    val lookups = getLookups(sessions)
    val fileSample = Sample()
    lookups.forEach { lookup ->
      for (suggestion in lookup.suggestions) {
        val value = if (suggestion.isRelevant) 1.0 else 0.0
        fileSample.add(value)
        coreSample.add(value)
      }
    }
    return fileSample.mean()
  }

  abstract fun getLookups(sessions: List<Session>): List<Lookup>
}

class PrecisionMetric : PrecisionMetricBase() {
  override val name = "Precision"
  override val description: String = "Ratio of selected proposals by all proposals"

  override fun getLookups(sessions: List<Session>): List<Lookup> {
    return sessions.flatMap { session -> session.lookups }
  }
}

class PrecisionWithRelevanceMetric(override val showByDefault: Boolean, private val relevance: String) : PrecisionMetricBase() {
  override val name = "Precision With ${relevance.capitalize()} Model"
  override val description: String = "Ratio of selected proposals by all proposals taking $relevance model into account"

  override fun getLookups(sessions: List<Session>): List<Lookup> {
    return sessions.flatMap { session -> session.lookups }.filter { it.additionalInfo["${relevance}_decision"] != "SKIP" }
  }
}