package com.intellij.cce.metric.context

import com.intellij.cce.core.Session
import com.intellij.cce.evaluable.AIA_CONTEXT
import com.intellij.cce.metric.Metric
import com.intellij.cce.metric.MetricValueType
import com.intellij.cce.metric.util.Sample

class MeanContextLines(override val name: String = "Mean Context Lines") : Metric {
  private val sample = Sample()
  override val description: String = "Average number of lines in context per session"
  override val showByDefault: Boolean = true
  override val valueType = MetricValueType.DOUBLE
  override val value: Double
    get() = sample.mean()

  override fun evaluate(sessions: List<Session>): Double {
    val fileSample = Sample()

    sessions
      .flatMap { session -> session.lookups }
      .forEach {
        val context = it.additionalInfo.getOrDefault(AIA_CONTEXT, "") as String
        val lines = context.lines()
        sample.add(lines.size)
        fileSample.add(lines.size)
      }
    return fileSample.mean()
  }
}
