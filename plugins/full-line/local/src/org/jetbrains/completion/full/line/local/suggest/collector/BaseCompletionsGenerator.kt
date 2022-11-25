package org.jetbrains.completion.full.line.local.suggest.collector

import io.kinference.model.ExecutionContext
import org.jetbrains.completion.full.line.local.CompletionModel
import org.jetbrains.completion.full.line.local.generation.generation.BaseGenerationConfig
import org.jetbrains.completion.full.line.local.generation.model.ModelWrapper
import org.jetbrains.completion.full.line.local.tokenizer.Tokenizer

/**
 * Base class for all completions generators that trims and cleans up completions
 * got from beam-search or other implementation before passing it to the client.
 */
internal abstract class BaseCompletionsGenerator<GenerationConfig : BaseGenerationConfig>(
  internal val model: ModelWrapper, internal val tokenizer: Tokenizer
) : CompletionsGenerator<GenerationConfig> {
  protected abstract fun generateWithSearch(
    context: String, prefix: String, config: GenerationConfig, execContext: ExecutionContext
  ): List<CompletionModel.CompletionResult>

  override fun generate(
    context: String, prefix: String, config: GenerationConfig, execContext: ExecutionContext
  ): List<CompletionModel.CompletionResult> {
    if (context.isBlank()) return emptyList()

    val seenCompletions = HashSet<String>()
    val completions = generateWithSearch(context, prefix, config, execContext)
    val result = ArrayList<CompletionModel.CompletionResult>()

    for (completion in completions) {
      val trimmedCompletion = trimCompletion(completion)
      val oneSpecificChar = trimmedCompletion.text.length == 1 && !completion.text[0].isLetterOrDigit()
      val containsInvalidSymbols = !tokenizer.isValidString(trimmedCompletion.text)
      if (trimmedCompletion.text.isEmpty() || oneSpecificChar || containsInvalidSymbols) continue

      val words = trimmedCompletion.text.trim().split(' ')
      val targetLen = words.size

      if (targetLen < config.minLen || trimmedCompletion.text in seenCompletions) continue
      trimmedCompletion.info.wordLen = targetLen

      seenCompletions.add(trimmedCompletion.text)
      result.add(trimmedCompletion)
    }

    return result
  }

  protected open fun trimCompletion(completion: CompletionModel.CompletionResult): CompletionModel.CompletionResult {
    return completion
  }
}
