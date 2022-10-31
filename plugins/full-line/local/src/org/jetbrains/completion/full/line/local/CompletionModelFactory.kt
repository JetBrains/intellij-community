package org.jetbrains.completion.full.line.local

import org.jetbrains.completion.full.line.local.generation.model.GPT2ModelWrapper
import org.jetbrains.completion.full.line.local.generation.model.HiddenStateCache
import org.jetbrains.completion.full.line.local.generation.model.HiddenStateCachingModelWrapper
import org.jetbrains.completion.full.line.local.generation.model.ModelWrapper
import org.jetbrains.completion.full.line.local.pipeline.FullLineCompletionPipeline
import org.jetbrains.completion.full.line.local.suggest.collector.FairSeqCompletionsGenerator
import org.jetbrains.completion.full.line.local.suggest.collector.FullLineCompletionsGenerator
import org.jetbrains.completion.full.line.local.suggest.filtering.ProbFilterModel
import org.jetbrains.completion.full.line.local.suggest.ranking.RawTotalProbRankingModel
import org.jetbrains.completion.full.line.local.suggest.ranking.WordTrieIterativeGolfRanking
import org.jetbrains.completion.full.line.local.tokenizer.BPETokenizer
import org.jetbrains.completion.full.line.local.tokenizer.FullLineTokenizer
import java.io.File

/**
 * Factory for creation Completion Models
 */
object CompletionModelFactory {
  /**
   * Factory for creation Completion Models
   *
   * @param config for model and tokenizer loading
   */
  fun createCompletionModel(config: CompletionConfig): CompletionModel {
    val tokenizer = BPETokenizer(config.loader)
    val model = GPT2ModelWrapper(config.loader, config.model)

    val generator = FairSeqCompletionsGenerator(model, tokenizer)
    val ranking = WordTrieIterativeGolfRanking(tokenizer, config.numSuggestions, -1000.0)
    val preFilter = ProbFilterModel()

    return CompletionModel(generator, ranking, preFilter)
  }

  fun createFullLineCompletionModel(
    tokenizerPath: File,
    modelPath: File,
    configPath: File,
    loggingCallback: ((String) -> Unit)? = null,
    modelCache: HiddenStateCache? = null
  ): FullLineCompletionPipeline {
    val tokenizer = FullLineTokenizer(tokenizerPath, nThreads = 2)

    var model: ModelWrapper = GPT2ModelWrapper(modelPath, configPath)
    if (modelCache != null) {
      model = HiddenStateCachingModelWrapper(GPT2ModelWrapper(modelPath, configPath), modelCache)
    }

    val generator = FullLineCompletionsGenerator(model, tokenizer, loggingCallback)
    val ranking = RawTotalProbRankingModel()

    return FullLineCompletionPipeline(generator, ranking, null)
  }
}
