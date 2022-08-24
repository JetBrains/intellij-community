package org.jetbrains.completion.full.line.models

import ml.intellij.nlc.local.CompletionModel
import ml.intellij.nlc.local.CompletionModelFactory
import ml.intellij.nlc.local.generation.model.HiddenStateCache
import ml.intellij.nlc.local.pipeline.CompletionPipeline
import ml.intellij.nlc.local.pipeline.FullLineCompletionPipelineConfig
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
    config: FullLineCompletionPipelineConfig
  ): List<CompletionResult> {
    cacheHitLength = 0
    val completions = fullLineCompletionPipeline.generateCompletions(context, prefix, config)
    return completions.map { CompletionResult(it, cacheHitLength) }
  }
}
