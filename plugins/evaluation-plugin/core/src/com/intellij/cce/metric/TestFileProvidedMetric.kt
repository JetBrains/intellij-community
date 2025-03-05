// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.metric

import com.intellij.cce.core.Session
import com.intellij.cce.evaluable.AIA_TEST_FILE_PROVIDED
import com.intellij.cce.metric.util.Sample

class TestFileProvidedMetric : Metric {
  private val sample = Sample()
  override val name: String = "Sessions With Test File"
  override val description: String = "Sessions with file path for test is provided"
  override val showByDefault: Boolean = true
  override val valueType = MetricValueType.DOUBLE
  override val value: Double
    get() = sample.mean()

  override fun evaluate(sessions: List<Session>): Double {
    val fileSample = Sample()

    sessions
      .flatMap { session -> session.lookups }
      .forEach {
        // If no metric value, return Nan, language is not supported
        val testFileProvided = it.additionalInfo.get(AIA_TEST_FILE_PROVIDED) as? Boolean ?: return Double.NaN
        if (testFileProvided) {
          sample.add(1.0)
          fileSample.add(1.0)
        }
        else {
          sample.add(0.0)
          fileSample.add(0.0)
        }
      }
    return fileSample.mean()
  }
}