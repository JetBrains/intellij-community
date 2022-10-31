package ml.intellij.nlc.local.pipeline

import ml.intellij.nlc.local.CompletionConfig
import ml.intellij.nlc.local.generation.generation.FullLineGenerationConfig
import ml.intellij.nlc.local.suggest.collector.FullLineCompletionsGenerator
import ml.intellij.nlc.local.suggest.filtering.FilterModel
import ml.intellij.nlc.local.suggest.ranking.RankingModel

class FullLineCompletionPipeline internal constructor(
    generator: FullLineCompletionsGenerator,
    rankingModel: RankingModel?,
    filterModel: FilterModel<CompletionConfig.Filter>?
) : BaseCompletionPipeline<FullLineGenerationConfig, CompletionConfig.Filter, FullLineCompletionPipelineConfig>(
    generator, rankingModel, filterModel
)