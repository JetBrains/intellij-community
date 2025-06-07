package com.intellij.cce.metric

import com.intellij.cce.core.Session
import com.intellij.cce.evaluable.AIA_ERASED_APIS
import com.intellij.cce.metric.util.Sample

class PreservedApi : Metric {
  override val name: String = "Preserved API"
  override val description: String = "Ratio of sessions where public API has not been erased"
  override val valueType: MetricValueType = MetricValueType.DOUBLE
  override val showByDefault: Boolean = true

  override val value: Double get() = sample.mean()

  private val sample = Sample()

  override fun evaluate(sessions: List<Session>): Double {
    val fileSample = Sample()
    sessions
      .flatMap { session -> session.lookups }
      .forEach { lookup ->
        val apis = lookup.additionalList(AIA_ERASED_APIS) ?: emptyList()
        val value = if (apis.isNotEmpty()) 0 else 1
        sample.add(value)
        fileSample.add(value)
      }
    return fileSample.mean()
  }
}