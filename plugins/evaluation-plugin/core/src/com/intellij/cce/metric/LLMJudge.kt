package com.intellij.cce.metric

import com.intellij.cce.core.Session
import com.intellij.cce.evaluable.AIA_LLM_JUDGE_SCORE_KEY
import com.intellij.cce.metric.util.Sample

class LLMJudgeScore : ConfidenceIntervalMetric<Double>() {

  override val showByDefault: Boolean = true
  override val valueType = MetricValueType.DOUBLE
  override val name: String = "LLM Judge Score"
  override val description: String = "Extracts llm as a judge score from lookup data"

  override val value: Double
    get() = compute(sample)

  override fun evaluate(sessions: List<Session>): Number {

    val fileSample = Sample()
    sessions
      .flatMap { it.lookups }
      .forEach {
        val llmJudgeScore = it.additionalInfo[AIA_LLM_JUDGE_SCORE_KEY] as? Double
        fileSample.add(llmJudgeScore ?: 0.0)
        coreSample.add(llmJudgeScore ?: 0.0)
      }
    return fileSample.mean()
  }

  override fun compute(sample: List<Double>): Double = sample.average()
}
