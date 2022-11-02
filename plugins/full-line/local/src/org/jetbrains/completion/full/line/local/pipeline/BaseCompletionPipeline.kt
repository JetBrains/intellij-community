package org.jetbrains.completion.full.line.local.pipeline

import org.jetbrains.completion.full.line.local.CompletionModel
import org.jetbrains.completion.full.line.local.ExecutionContext
import org.jetbrains.completion.full.line.local.generation.generation.BaseGenerationConfig
import org.jetbrains.completion.full.line.local.suggest.collector.CompletionsGenerator
import org.jetbrains.completion.full.line.local.suggest.filtering.FilterModel
import org.jetbrains.completion.full.line.local.suggest.ranking.RankingModel

abstract class BaseCompletionPipeline<GenerationConfig : BaseGenerationConfig, FilterConfig, PipelineConfig : BaseCompletionPipelineConfig<GenerationConfig, FilterConfig>>(
  private val generator: CompletionsGenerator<GenerationConfig>,
  private val rankingModel: RankingModel?,
  private val filterModel: FilterModel<FilterConfig>?
) : CompletionPipeline<PipelineConfig, CompletionModel.CompletionResult> {

  override fun generateCompletions(
    context: String, prefix: String, config: PipelineConfig, execContext: ExecutionContext
  ): List<CompletionModel.CompletionResult> {
    var completions = generate(context, prefix, config.generationConfig, execContext)
    completions = filter(context, prefix, completions, config.filterConfig)
    completions = rank(context, prefix, completions)
    config.numSuggestions?.let { completions = completions.take(it) }
    return completions
  }

  protected open fun generate(
    context: String, prefix: String, config: GenerationConfig, execContext: ExecutionContext
  ): List<CompletionModel.CompletionResult> {
    return generator.generate(context, prefix, config, execContext.toInference())
  }

  protected open fun filter(
    context: String, prefix: String, completions: List<CompletionModel.CompletionResult>, config: FilterConfig
  ): List<CompletionModel.CompletionResult> {
    return filterModel?.filter(context, prefix, completions, config) ?: completions
  }

  protected open fun rank(
    context: String, prefix: String, completions: List<CompletionModel.CompletionResult>
  ): List<CompletionModel.CompletionResult> {
    return rankingModel?.rank(context, prefix, completions) ?: completions
  }
}
