package org.jetbrains.completion.full.line.local.generation.model

import io.kinference.model.ExecutionContext
import org.jetbrains.completion.full.line.local.generation.generation.FullLineGenerationConfig

class HiddenStateCachingModelWrapper(
  private val delegate: ModelWrapper, private val cache: HiddenStateCache
) : ModelWrapper by delegate {

  override fun initLastLogProbs(inputIds: Array<IntArray>, execContext: ExecutionContext): ModelOutput {
    val cacheQueryResult = cache.query(inputIds[0])
    val modelOutput = getModelOutput(inputIds[0], cacheQueryResult, execContext)
    if (cacheQueryResult.cacheOutdated) {
      cache.cache(inputIds[0], modelOutput)
    }
    return modelOutput
  }

  override fun composeInputIds(metaInfoIds: IntArray, contextIds: IntArray, config: FullLineGenerationConfig): IntArray =
    cache.composeInputIds(metaInfoIds, contextIds, maxSeqLen, config)

  private fun getModelOutput(
    inputIds: IntArray, cacheQueryResult: HiddenStateCache.QueryResult, execContext: ExecutionContext
  ): ModelOutput {
    val (newInputIds, pastStates, modelOutput) = cacheQueryResult
    return when {
      modelOutput != null -> modelOutput
      pastStates != null -> delegate.getLogProbs(arrayOf(newInputIds), pastStates, execContext).lastLogProbs()
      else -> delegate.initLastLogProbs(arrayOf(inputIds), execContext)
    }
  }
}
