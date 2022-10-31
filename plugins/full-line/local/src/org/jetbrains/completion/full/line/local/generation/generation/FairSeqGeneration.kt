package org.jetbrains.completion.full.line.local.generation.generation

import io.kinference.model.ExecutionContext
import org.jetbrains.completion.full.line.local.CompletionConfig
import org.jetbrains.completion.full.line.local.generation.model.ModelWrapper
import org.jetbrains.completion.full.line.local.generation.search.BeamSearch
import org.jetbrains.completion.full.line.local.tokenizer.Tokenizer

internal class FairSeqGeneration(model: ModelWrapper, tokenizer: Tokenizer) :
  BaseGeneration<CompletionConfig.Generation>(model, tokenizer) {
  override fun generate(
    context: IntArray,
    prefix: String,
    config: CompletionConfig.Generation,
    execContext: ExecutionContext
  ): List<List<GenerationInfo>> {
    val search = BeamSearch(vocabSize, config.numBeams, config.repetitionPenalty)

    initState(prefix, config)
    initLogProbs(context, execContext)
    sortState(IntArray(search.batchSize))

    val result = ArrayList<List<GenerationInfo>>()
    for (i in 0 until config.maxLen) {
      val stepResult = search.step(nextLogProbs!!, context)
      updateState(stepResult.sortMask, stepResult.newTokens)

      if (i < config.maxLen - 1) {
        updateLogProbs(stepResult.newTokens, execContext)
      }
      result.add(currentHypotheses(search))
    }

    resetState()
    return result
  }
}
