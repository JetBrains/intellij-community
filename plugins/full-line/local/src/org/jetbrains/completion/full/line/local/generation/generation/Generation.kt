package org.jetbrains.completion.full.line.local.generation.generation

import io.kinference.model.ExecutionContext
import org.jetbrains.completion.full.line.local.generation.model.ModelWrapper
import org.jetbrains.completion.full.line.local.tokenizer.Tokenizer

internal interface Generation<GenerationConfig> {
  val model: ModelWrapper
  val tokenizer: Tokenizer

  fun generate(
    context: IntArray, prefix: String, config: GenerationConfig, execContext: ExecutionContext
  ): List<List<GenerationInfo>>
}
