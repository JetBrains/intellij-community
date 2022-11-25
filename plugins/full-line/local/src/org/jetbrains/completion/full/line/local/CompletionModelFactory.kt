package org.jetbrains.completion.full.line.local

import org.jetbrains.completion.full.line.local.generation.model.GPT2ModelWrapper
import org.jetbrains.completion.full.line.local.generation.model.HiddenStateCache
import org.jetbrains.completion.full.line.local.generation.model.HiddenStateCachingModelWrapper
import org.jetbrains.completion.full.line.local.pipeline.FullLineCompletionPipeline
import org.jetbrains.completion.full.line.local.suggest.collector.FullLineCompletionsGenerator
import org.jetbrains.completion.full.line.local.suggest.ranking.RawTotalProbRankingModel
import org.jetbrains.completion.full.line.local.tokenizer.FullLineTokenizer
import java.io.File

/**
 * Factory for creation Completion Models
 */
object CompletionModelFactory {

  fun createFullLineCompletionModel(
    tokenizerPath: File,
    modelPath: File,
    configPath: File,
    loggingCallback: ((String) -> Unit)? = null,
    modelCache: HiddenStateCache? = null
  ): FullLineCompletionPipeline {
    val tokenizer = FullLineTokenizer(tokenizerPath)

    val model = if (modelCache == null) GPT2ModelWrapper(modelPath, configPath)
                else HiddenStateCachingModelWrapper(GPT2ModelWrapper(modelPath, configPath), modelCache)

    val generator = FullLineCompletionsGenerator(model, tokenizer, loggingCallback)
    val ranking = RawTotalProbRankingModel()

    return FullLineCompletionPipeline(generator, ranking, null)
  }
}
