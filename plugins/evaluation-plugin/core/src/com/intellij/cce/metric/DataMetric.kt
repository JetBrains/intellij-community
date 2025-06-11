package com.intellij.cce.metric

import com.intellij.cce.core.Session
import com.intellij.cce.evaluation.data.DataProps
import com.intellij.cce.evaluation.data.EvalDataDescription
import com.intellij.cce.metric.util.Sample

class DataMetric<T>(
  private val data: EvalDataDescription<*, T>,
  private val f: (T, DataProps) -> Double,
  override val name: String = data.name,
) : Metric {
  private val sample = Sample()
  override val description: String = data.description ?: ""
  override val showByDefault: Boolean = true

  override val valueType: MetricValueType = MetricValueType.DOUBLE
  override val value: Double get() = sample.mean()

  override fun evaluate(sessions: List<Session>): Double {
    val fileSample = Sample()

    for (session in sessions) {
      for (lookup in session.lookups) {
        val dataProps = DataProps(
          null, // TODO
          null, // TODO
          session,
          lookup
        )

        val value = data.data.placement.first(dataProps)?.let { f(it, dataProps) } ?: 0.0
        sample.add(value)
        fileSample.add(value)
      }
    }

    return fileSample.mean()
  }
}