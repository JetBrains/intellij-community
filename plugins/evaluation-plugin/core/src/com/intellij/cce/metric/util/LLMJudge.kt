package com.intellij.cce.metric.util

data class LLMJudgeResult(
  val response: String,
  val score: Double?,
)

interface LLMJudge {
  fun computeLLMJudgeScoreSync(question: String, aiaResponse: String, reference: String): LLMJudgeResult
}
