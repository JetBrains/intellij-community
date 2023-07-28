// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.metric

import com.intellij.cce.core.Lookup
import com.intellij.cce.core.Session

interface Metric {
  fun evaluate(sessions: List<Session>): Number

  fun confidenceInterval(): Pair<Double, Double>? = null

  fun mapSessionsToLookups(sessions: List<Session>): List<Lookup> {
    return sessions
      .flatMap { session -> session.lookups }
  }

  val value: Double

  val name: String

  val description: String

  val valueType: MetricValueType

  val showByDefault: Boolean
    get() = true
}
