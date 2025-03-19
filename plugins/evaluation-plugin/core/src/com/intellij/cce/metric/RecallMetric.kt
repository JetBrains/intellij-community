// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.metric

import com.intellij.cce.core.Lookup
import com.intellij.cce.core.Session
import com.intellij.cce.metric.util.Sample

abstract class RecallMetricBase : Metric {
  private val sample = Sample()
  override val showByDefault: Boolean = true
  override val valueType = MetricValueType.DOUBLE
  override val value: Double
    get() = sample.mean()

  override fun evaluate(sessions: List<Session>): Double {
    val lookups = sessions.flatMap { session -> session.lookups }

    val fileSample = Sample()
    lookups
      .forEach { lookup ->
        val value = if (hasRelevant(lookup)) 1.0 else 0.0
        fileSample.add(value)
        sample.add(value)
      }

    return fileSample.mean()
  }

  abstract fun hasRelevant(lookup: Lookup): Boolean
}

class RecallMetric : RecallMetricBase() {
  override val name = "Recall"
  override val description = "Ratio of successful invocations"

  override fun hasRelevant(lookup: Lookup): Boolean {
    return lookup.suggestions.any { it.isRelevant }
  }
}

class RecallAtMetric(override val showByDefault: Boolean, private val n: Int) : RecallMetricBase() {
  override val name = "RecallAt$n"
  override val description: String = "Ratio of invocations with matching proposal in top-$n"

  override fun hasRelevant(lookup: Lookup): Boolean {
    val indexOfRelevantSuggestion = lookup.suggestions.indexOfFirst { it.isRelevant }
    return indexOfRelevantSuggestion in 0 until n
  }
}

class RecallWithRelevanceMetric(override val showByDefault: Boolean, private val relevance: String) : RecallMetricBase() {
  override val name = "Recall With ${relevance.capitalize()} Model"
  override val description: String = "Ratio of invocations with matching proposal taking $relevance model into account"

  override fun hasRelevant(lookup: Lookup): Boolean {
    return lookup.additionalInfo["${relevance}_decision"] != "SKIP" && lookup.suggestions.any { it.isRelevant }
  }
}
