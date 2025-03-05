package com.intellij.cce.metric

import com.intellij.cce.core.Session
import com.intellij.cce.evaluable.AIA_HIGHLIGHT_ERRORS
import com.intellij.cce.metric.util.Sample

class WithoutHighlightErrorsSessionRatio : Metric {
  override val name: String = "Without Highlight Errors"
  override val description: String = "Ratio of sessions without highlight errors appeared after modification"
  override val valueType: MetricValueType = MetricValueType.DOUBLE
  override val showByDefault: Boolean = true

  override val value: Double get() = sample.mean()

  private val sample = Sample()

  override fun evaluate(sessions: List<Session>): Double {
    val fileSample = Sample()
    sessions
      .flatMap { session -> session.lookups }
      .forEach { lookup ->
        val highlights = lookup.additionalList(AIA_HIGHLIGHT_ERRORS) ?: emptyList()
        val withoutErrors = if (highlights.any { it.startsWith("[ERROR]") }) 0 else 1
        sample.add(withoutErrors)
        fileSample.add(withoutErrors)
      }
    return fileSample.mean()
  }
}