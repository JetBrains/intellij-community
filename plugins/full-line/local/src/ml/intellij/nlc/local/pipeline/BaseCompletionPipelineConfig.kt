package ml.intellij.nlc.local.pipeline

import ml.intellij.nlc.local.generation.generation.BaseGenerationConfig


abstract class BaseCompletionPipelineConfig<GenerationConfig : BaseGenerationConfig, FilterConfig>(
    val generationConfig: GenerationConfig,
    val filterConfig: FilterConfig,
    open val numSuggestions: Int?
)
