package ml.intellij.nlc.local

import io.kinference.model.ExecutionContext
import ml.intellij.nlc.local.generation.generation.GenerationInfo
import ml.intellij.nlc.local.pipeline.CompletionPipeline
import ml.intellij.nlc.local.suggest.collector.CompletionsGenerator
import ml.intellij.nlc.local.suggest.filtering.FilterModel
import ml.intellij.nlc.local.suggest.ranking.RankingModel
import kotlin.math.min


/**
 * Model that performs natural language completion based on context and prefix
 *
 * Under the hood, model would use GPT-like model and beam-search depending
 * on [generator] use
 *
 * @param generator used to get completions
 * @param ranking used to rank completions got from generator
 * @param preFilter would be used to filter completions before ranking
 */
class CompletionModel(
    private val generator: CompletionsGenerator<CompletionConfig.Generation>,
    private val ranking: RankingModel?,
    private val preFilter: FilterModel<CompletionConfig.Filter>?
) : CompletionPipeline<CompletionConfig, CompletionModel.CompletionResult> {
    /** Result of completion generation -- text and metadata (probabilities, etc.) */
    data class CompletionResult(val text: String, val info: GenerationInfo)

    fun generate(
        context: String, prefix: String, config: CompletionConfig.Generation, execContext: ExecutionContext
    ): List<CompletionResult> {
        return generator.generate(context, prefix, config, execContext)
    }

    /**
     * Get completions for [context] and [prefix] with respect to [config] configuration
     *
     * Note, that complete expects that context would not have trailing whitespaces and prefix
     * would have leading whitespaces (or even consist of only whitespaces).
     *
     * So, if you have something like `Hello wo` you should split it into `context: "Hello"`
     * and `prefix: " wo"`
     */
    override fun generateCompletions(
        context: String,
        prefix: String,
        config: CompletionConfig,
        execContext: ExecutionContext
    ): List<CompletionResult> {
        return complete(context, prefix, config.numSuggestions, config.generationConfig, config.filterConfig, execContext)
    }

    fun complete(
        context: String, prefix: String, config: CompletionConfig, execContext: ExecutionContext
    ): List<String> {
        return generateCompletions(context, prefix, config, execContext).map { it.text }
    }

    private fun complete(
        context: String,
        prefix: String,
        numSuggestions: Int?,
        generationConfig: CompletionConfig.Generation,
        filterConfig: CompletionConfig.Filter?,
        execContext: ExecutionContext
    ): List<CompletionResult> {
        var completions = generate(context, prefix, generationConfig, execContext)

        if (preFilter != null && filterConfig != null) {
            completions = preFilter.filter(context, prefix, completions, filterConfig)
        }

        if (ranking != null) {
            completions = ranking.rank(context, prefix, completions)
        }

        return completions.take(min(numSuggestions ?: Int.MAX_VALUE, completions.size))
    }
}
