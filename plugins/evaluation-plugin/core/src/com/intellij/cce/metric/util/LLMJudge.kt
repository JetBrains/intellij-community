package com.intellij.cce.metric.util

interface LLMJudge {
  fun computeLLMJudgeScoreSync(question: String, aiaResponse: String, reference: String): Pair<Double?, String>
}
