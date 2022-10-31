package ml.intellij.nlc.local.generation.generation

import io.kinference.model.ExecutionContext
import ml.intellij.nlc.local.generation.model.ModelWrapper
import ml.intellij.nlc.local.tokenizer.Tokenizer

internal interface Generation<GenerationConfig> {
  val model: ModelWrapper
  val tokenizer: Tokenizer

  fun generate(
    context: IntArray, prefix: String, config: GenerationConfig, execContext: ExecutionContext
  ): List<List<GenerationInfo>>
}
