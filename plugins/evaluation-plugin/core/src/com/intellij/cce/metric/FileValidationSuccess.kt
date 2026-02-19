package com.intellij.cce.metric

import com.intellij.cce.core.Session
import com.intellij.cce.evaluable.AIA_FAILED_FILE_VALIDATIONS
import com.intellij.cce.metric.util.Sample

class FileValidationSuccess : Metric {
  override val name: String = "File Validation Success"
  override val description: String = "Ratio of sessions without file validation failures"
  override val valueType: MetricValueType = MetricValueType.DOUBLE
  override val showByDefault: Boolean = true

  override val value: Double get() = sample.mean()

  private val sample = Sample()

  override fun evaluate(sessions: List<Session>): Double {
    val fileSample = Sample()
    sessions
      .flatMap { it.lookups }
      .forEach { lookup ->
        val failedValidations = lookup.additionalList(AIA_FAILED_FILE_VALIDATIONS) ?: emptyList()
        val success = if (failedValidations.isEmpty()) 1 else 0
        sample.add(success)
        fileSample.add(success)
      }
    return fileSample.mean()
  }
}