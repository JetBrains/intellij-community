package org.jetbrains.completion.full.line.models

import io.kinference.model.ExecutionContext
import org.jetbrains.completion.full.line.local.CompletionModel
import org.jetbrains.completion.full.line.local.CompletionModelFactory
import org.jetbrains.completion.full.line.local.generation.model.HiddenStateCache
import org.jetbrains.completion.full.line.local.pipeline.CompletionPipeline
import org.jetbrains.completion.full.line.local.pipeline.FullLineCompletionPipelineConfig
import java.io.File

class CachingLocalPipeline(
  tokenizerPath: File, modelPath: File, configPath: File, loggingCallback: ((String) -> Unit)?
) : CompletionPipeline<FullLineCompletionPipelineConfig, CachingLocalPipeline.CompletionResult> {
  class CompletionResult(val fullLineCompletionResult: CompletionModel.CompletionResult, val cacheHitLength: Int)

  var cacheHitLength = 0
  private val modelCache = object : HiddenStateCache() {
    override fun onCacheHit(commonPrefixLength: Int) {
      cacheHitLength = commonPrefixLength
    }
  }
  private val fullLineCompletionPipeline =
    CompletionModelFactory.createFullLineCompletionModel(tokenizerPath, modelPath, configPath, loggingCallback, modelCache)

  @Synchronized
  override fun generateCompletions(
    context: String,
    prefix: String,
    config: FullLineCompletionPipelineConfig,
    execContext: ExecutionContext,
  ): List<CompletionResult> {
    cacheHitLength = 0
    val completions = fullLineCompletionPipeline.generateCompletions(context, prefix, config, execContext)
    return completions.map { CompletionResult(it, cacheHitLength) }
  }
}
