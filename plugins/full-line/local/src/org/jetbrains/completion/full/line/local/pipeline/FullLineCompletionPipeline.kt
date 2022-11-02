package org.jetbrains.completion.full.line.local.pipeline

import org.jetbrains.completion.full.line.local.CompletionConfig
import org.jetbrains.completion.full.line.local.generation.generation.FullLineGenerationConfig
import org.jetbrains.completion.full.line.local.suggest.collector.FullLineCompletionsGenerator
import org.jetbrains.completion.full.line.local.suggest.filtering.FilterModel
import org.jetbrains.completion.full.line.local.suggest.ranking.RankingModel

class FullLineCompletionPipeline internal constructor(
  generator: FullLineCompletionsGenerator,
  rankingModel: RankingModel?,
  filterModel: FilterModel<CompletionConfig.Filter>?
) : BaseCompletionPipeline<FullLineGenerationConfig, CompletionConfig.Filter, FullLineCompletionPipelineConfig>(
  generator, rankingModel, filterModel
)
