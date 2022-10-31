package org.jetbrains.completion.full.line.local.pipeline

import org.jetbrains.completion.full.line.local.generation.generation.BaseGenerationConfig

abstract class BaseCompletionPipelineConfig<GenerationConfig : BaseGenerationConfig, FilterConfig>(
  val generationConfig: GenerationConfig,
  val filterConfig: FilterConfig,
  open val numSuggestions: Int?
)
