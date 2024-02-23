// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.metric

import com.intellij.cce.core.Lookup
import com.intellij.cce.core.Session
import com.intellij.cce.metric.util.Sample

open class RecallMetric : Metric {
  protected val sample = Sample()
  override val name = NAME
  override val description: String = "Ratio of successful invocations"
  override val showByDefault: Boolean = true
  override val valueType = MetricValueType.DOUBLE
  override val value: Double
    get() = sample.mean()

  override fun evaluate(sessions: List<Session>): Double {
    val lookups = sessions.flatMap { session -> session.lookups }

    val fileSample = Sample()
    lookups
      .forEach { lookup ->
        val value = if (suggestionRelevant(lookup)) 1.0 else 0.0
        fileSample.add(value)
        sample.add(value)
      }

    return fileSample.mean()
  }

  protected open fun suggestionRelevant(lookup: Lookup): Boolean {
    return lookup.suggestions.any { it.isRelevant }
  }

  companion object {
    const val NAME = "Recall"
  }
}

class RecallAtMetric(override val showByDefault: Boolean, private val n: Int) : RecallMetric() {
  override val name = NAME_PREFIX + n
  override val description: String = "Ratio of invocations with matching proposal in top-$n"
  override val valueType = MetricValueType.DOUBLE

  override fun suggestionRelevant(lookup: Lookup): Boolean {
    val indexOfRelevantSuggestion = lookup.suggestions.indexOfFirst { it.isRelevant }
    return indexOfRelevantSuggestion in 0 until n
  }

  companion object {
    const val NAME_PREFIX = "RecallAt"
  }
}

class RecallWithRelevanceMetric(override val showByDefault: Boolean, private val relevance: String) : RecallMetric() {
  override val name = "Recall With ${relevance.capitalize()} Model"
  override val description: String = "Ratio of invocations with matching proposal taking $relevance model into account"

  override fun suggestionRelevant(lookup: Lookup): Boolean {
    return lookup.additionalInfo["${relevance}_decision"] != "SKIP" && lookup.suggestions.any { it.isRelevant }
  }
}
