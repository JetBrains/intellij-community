// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.metric

import com.intellij.cce.core.Session
import com.intellij.cce.evaluable.AIA_HAS_SYNTAX_ERRORS
import com.intellij.cce.metric.util.Sample

class WithoutSyntaxErrorsSessionRatio : Metric {
  private val sample = Sample()
  override val name: String = "Sessions Without Syntax Errors Ratio"
  override val description: String = "Ratio of sessions without syntax errors"
  override val showByDefault: Boolean = true
  override val valueType = MetricValueType.DOUBLE
  override val value: Double
    get() = sample.mean()

  override fun evaluate(sessions: List<Session>): Double {
    val fileSample = Sample()

    sessions
      .flatMap { session -> session.lookups }
      .forEach {
        if (it.additionalInfo.getOrDefault(AIA_HAS_SYNTAX_ERRORS, true) as Boolean) {
          sample.add(0.0)
          fileSample.add(0.0)
        }
        else{
          sample.add(1.0)
          fileSample.add(1.0)
        }
      }
    return fileSample.mean()
  }
}
