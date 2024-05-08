package com.intellij.cce.evaluable.docGeneration

import com.intellij.cce.evaluable.EvaluationStrategy
import com.intellij.cce.filter.EvaluationFilter

class DocGenerationStrategy(val suggestionsProvider: String = DEFAULT_PROVIDER) : EvaluationStrategy {
  override val filters: Map<String, EvaluationFilter> = emptyMap()

  companion object {
    private const val DEFAULT_PROVIDER: String = "LLM-doc-gen"
  }
}
