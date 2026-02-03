package com.intellij.cce.metric

import com.intellij.cce.core.Session
import com.intellij.cce.evaluable.AIA_ACTUAL_FUNCTION_CALLS
import com.intellij.cce.evaluable.AIA_EXPECTED_FUNCTION_CALLS
import com.intellij.cce.metric.util.Sample

class FunctionCallingMetric : Metric {
  override val name: String = "Function Calling"
  override val description: String = "Ratio of sessions where all expected function calls have been done"
  override val valueType: MetricValueType = MetricValueType.DOUBLE
  override val showByDefault: Boolean = true

  override val value: Double get() = sample.mean()

  private val sample = Sample()

  override fun evaluate(sessions: List<Session>): Double {
    val fileSample = Sample()
    sessions
      .flatMap { it.lookups }
      .forEach { lookup ->
        val actualFunctionCalls = lookup.additionalList(AIA_ACTUAL_FUNCTION_CALLS) ?: emptyList()
        val expectedFunctionCalls = lookup.additionalList(AIA_EXPECTED_FUNCTION_CALLS) ?: emptyList()
        val success = if (expectedFunctionCalls.all { actualFunctionCalls.contains(it) }) 1 else 0
        sample.add(success)
        fileSample.add(success)
      }
    return fileSample.mean()
  }
}