package ml.intellij.nlc.local.suggest.collector

import io.kinference.model.ExecutionContext
import ml.intellij.nlc.local.CompletionModel

/**
 * Generator of completions for specific context and prefix.
 * Under the hood implementation may use beam search or other techniques to get suggestions from GPT-like model
 */
interface CompletionsGenerator<GenerationConfig> {
    /**
     * Perform generation of completions from specific [context] and [prefix] with [config] configuration
     *
     * Note, that completions would start from [prefix]
     *
     * Also, it is expected that context would not have trailing whitespaces and prefix would have leading whitespaces
     * or even consist of only whitespaces.
     */
    fun generate(
        context: String, prefix: String, config: GenerationConfig, execContext: ExecutionContext
    ): List<CompletionModel.CompletionResult>
}
