package org.jetbrains.completion.full.line.local

import org.apache.commons.lang3.RandomStringUtils
import org.jetbrains.completion.full.line.local.generation.model.HiddenStateCache
import org.jetbrains.completion.full.line.local.pipeline.FullLineCompletionPipeline
import org.jetbrains.completion.full.line.local.pipeline.FullLineCompletionPipelineConfig

class CompletionModelBenchmarkHelper(private val random: java.util.Random) {
  private val modelFiles = ModelsFiles.currentModel

  private val hiddenStateCache = object : HiddenStateCache() {
    override fun onCacheHit(commonPrefixLength: Int) {
      _cacheHits++
    }
  }
  private var completionModel = CompletionModelFactory.createFullLineCompletionModel(
    modelFiles.tokenizer,
    modelFiles.model,
    modelFiles.config,
    modelCache = hiddenStateCache
  )

  private var _cacheHits = 0
  fun resetCacheHits() {
    _cacheHits = 0
  }

  fun resetCache() {
    hiddenStateCache.reset()
  }

  val cacheHits
    get() = _cacheHits


  /**
   * This function is called in benchmarks,
   * so it should not contain any logic other than calling [FullLineCompletionPipeline.generateCompletions]
   */
  fun generate(context: String, prefix: String, config: FullLineCompletionPipelineConfig): List<CompletionModel.CompletionResult> {
    return completionModel.generateCompletions(context, prefix, config, TestExecutionContext.default)
  }

  fun getConfig(maxLen: Int, filename: String): FullLineCompletionPipelineConfig {
    return FullLineCompletionPipelineConfig(maxLen = maxLen, filename = filename)
  }

  fun randomFilename(filenameLen: Int): String {
    require(filenameLen >= 0)
    return RandomStringUtils.random(filenameLen, 32, 126, false, false, null, random)
  }

  fun randomPrefix(prefixLen: Int): String {
    require(prefixLen >= 0)
    if (prefixLen == 0) {
      return ""
    }
    return " " + RandomStringUtils.random(prefixLen, 0, 0, true, false, null, random)
  }

  /**
   * Generates a random context of length [contextLen] or [contextLen] + 1
   */
  private fun randomContext(contextLen: Int): String {
    require(contextLen >= 0)
    val context = RandomStringUtils.random(contextLen, 32, 126, false, false, null, random)
    if (context.endsWith(" ")) {
      return context + RandomStringUtils.random(1, 0, 0, true, true, null, random).single()
    }
    else {
      return context
    }
  }

  fun continueContextRandomly(context: String?, contextLen: Int, offsetContext: Int): String {
    require(contextLen >= 0)
    require(offsetContext >= 0)
    return if (context == null || offsetContext >= context.length) {
      randomContext(contextLen)
    }
    else {
      context.substring(offsetContext) + randomContext(offsetContext)
    }
  }
}