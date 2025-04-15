// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.metric

import com.intellij.cce.core.Session
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.thisLogger

interface Metric {
  val supportsIndividualScores: Boolean
    get() = false

  fun evaluate(sessions: List<Session>): Number

  fun evaluateWithIndividualScores(sessions: List<Session>): MetricEvaluationResult {
    if (!supportsIndividualScores) {
      LOG.warn("$name does not support individual scores; evaluateWithIndividualScores will return an empty map.")
    }
    val overallScore = evaluate(sessions)
    return MetricEvaluationResult(
      overallScore = overallScore,
      sessionIndividualScores = emptyMap()
    )
  }
  fun confidenceInterval(): Pair<Double, Double>? = null

  val value: Double

  val name: String

  val description: String

  val valueType: MetricValueType

  val showByDefault: Boolean

  val maximumSessions: Int
    get() = 75000

  fun shouldComputeIntervals(numberOfSessions: Int): Boolean =
    numberOfSessions <= maximumSessions && System.getenv("cce_compute_confidence_intervals")?.toBooleanStrictOrNull() == true

  companion object {
    val LOG: Logger = thisLogger()
  }
}
