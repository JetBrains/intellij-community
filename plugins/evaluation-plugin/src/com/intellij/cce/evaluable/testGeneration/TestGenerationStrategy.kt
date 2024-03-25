package com.intellij.cce.evaluable.testGeneration

import com.intellij.cce.evaluable.EvaluationStrategy
import com.intellij.cce.filter.EvaluationFilter

class TestGenerationStrategy(val suggestionsProvider: String = DEFAULT_PROVIDER) : EvaluationStrategy {
  override val filters: Map<String, EvaluationFilter> = emptyMap()

  companion object {
    private const val DEFAULT_PROVIDER: String = "LLM-test-gen"
  }
}
