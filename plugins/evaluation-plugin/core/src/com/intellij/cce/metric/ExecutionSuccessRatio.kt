package com.intellij.cce.metric

import com.intellij.cce.core.Session
import com.intellij.cce.evaluable.AIA_EXECUTION_SUCCESS_RATIO
import com.intellij.cce.metric.util.Sample

class ExecutionSuccessRatio : Metric {
  private val sample = Sample()
  override val name: String = "Tests Without Errors Ratio"
  override val description: String = "Percentage of test cases that are executed without any errors or failure"
  override val showByDefault: Boolean = true
  override val valueType = MetricValueType.DOUBLE
  override val value: Double
    get() = sample.mean()

  override fun evaluate(sessions: List<Session>): Double {
    val fileSample = Sample()

    sessions
      .flatMap { session -> session.lookups }
      .forEach {
        val successRatio =
          it.additionalInfo.getOrDefault(AIA_EXECUTION_SUCCESS_RATIO, 0.0) as Double

        sample.add(successRatio)
        fileSample.add(successRatio)
      }
    return fileSample.mean()
  }

}