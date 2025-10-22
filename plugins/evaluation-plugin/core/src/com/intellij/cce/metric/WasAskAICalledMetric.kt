package com.intellij.cce.metric

import com.intellij.cce.core.Session
import com.intellij.cce.evaluable.AIA_ACTUAL_SMART_CHAT_ENDPOINTS
import com.intellij.cce.metric.util.Sample

class WasAskAICalledMetric : ConfidenceIntervalMetric<Double>() {

  override val showByDefault: Boolean = true
  override val valueType: MetricValueType = MetricValueType.DOUBLE
  override val name: String = "Was Ask AI Called ratio"
  override val description: String = "Ratio of cases, where Ask AI feature was called"

  override val value: Double
    get() = compute(sample)

  override fun evaluate(sessions: List<Session>): Number {
    val fileSample = Sample()
    sessions
      .flatMap { it.lookups }
      .forEach {
        val calledSmartChatEndpoints = it.additionalInfo[AIA_ACTUAL_SMART_CHAT_ENDPOINTS]
        val wasAskAICalled = if (calledSmartChatEndpoints == "interact_with_ide") 1.0 else 0.0
        fileSample.add(wasAskAICalled)
        coreSample.add(wasAskAICalled)
      }
    return fileSample.mean()
  }

  override fun compute(sample: List<Double>): Double = sample.average()
}
