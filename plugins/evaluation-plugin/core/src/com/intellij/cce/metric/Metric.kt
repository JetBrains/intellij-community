// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.metric

import com.intellij.cce.core.Session

interface Metric {
  fun evaluate(sessions: List<Session>): Number

  fun confidenceInterval(): Pair<Double, Double>? = null

  val value: Double

  val name: String

  val description: String

  val valueType: MetricValueType

  val showByDefault: Boolean

  val shouldComputeIntervals: Boolean
    get() = System.getenv("cce_compute_confidence_intervals")?.toBooleanStrictOrNull() == true
}
