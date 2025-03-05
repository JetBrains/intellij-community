// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.metric

import com.intellij.cce.workspace.info.SessionIndividualScore

class MetricInfo(
  name: String,
  val description: String,
  val value: Double,
  val confidenceInterval: Pair<Double, Double>?,
  evaluationType: String,
  val valueType: MetricValueType,
  val showByDefault: Boolean,
  val individualScores: Map<String, SessionIndividualScore>?) {

  val name = name.filter { it.isLetterOrDigit() || it == '_' }
  val evaluationType = evaluationType.filter { it.isLetterOrDigit() || it == '_' }
}
