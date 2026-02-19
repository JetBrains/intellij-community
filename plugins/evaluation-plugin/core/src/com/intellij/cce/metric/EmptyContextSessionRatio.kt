package com.intellij.cce.metric

import com.intellij.cce.core.Session
import com.intellij.cce.metric.util.Sample

class EmptyContextSessionRatio(override val name: String = "EmptyContextSessionRatio") : Metric {
  private val sample = Sample()
  override val description: String = "EmptyContextSessionRatio"
  override val showByDefault: Boolean = true
  override val valueType = MetricValueType.DOUBLE
  override val value: Double
    get() = sample.mean()

  override fun evaluate(sessions: List<Session>): Double {
    val fileSample = Sample()

    sessions
      .flatMap { session -> session.lookups }
      .forEach {
        val context = it.additionalInfo["context"]
        if (context is Map<*, *>) {
          if (context["contents"] == "") {
            sample.add(1.0)
            fileSample.add(1.0)
          }
          else{
            sample.add(0.0)
            fileSample.add(0.0)
          }
        }
      }
    return fileSample.mean()
  }
}