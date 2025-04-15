package com.intellij.cce.metric

import com.intellij.cce.core.Session
import com.intellij.cce.evaluable.AIA_AST_MATCH
import com.intellij.cce.metric.util.Sample

class AstMatchMetric : Metric {
  override val name: String = "AST Match"
  override val description: String = "Ratio of sessions where AST of predicted result completely matched with expected"
  override val valueType: MetricValueType = MetricValueType.DOUBLE
  override val showByDefault: Boolean = true

  override val value: Double get() = sample.mean()

  private val sample = Sample()

  override fun evaluate(sessions: List<Session>): Double {
    val fileSample = Sample()
    sessions
      .flatMap { session -> session.lookups }
      .forEach { lookup ->
        val matched = lookup.additionalInfo[AIA_AST_MATCH] as? Double ?: 0.0
        sample.add(matched)
        fileSample.add(matched)
      }
    return fileSample.mean()
  }
}