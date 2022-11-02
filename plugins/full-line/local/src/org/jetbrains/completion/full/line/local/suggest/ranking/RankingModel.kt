package org.jetbrains.completion.full.line.local.suggest.ranking

import org.jetbrains.completion.full.line.local.CompletionModel

/**
 * Interface for ranking models that would reorder completions
 * based on information from completion model, prefix and context
 */
interface RankingModel {
  /**
   * Performs reordering of completions based on information from model, context and prefix
   */
  fun rank(context: String, prefix: String, completions: List<CompletionModel.CompletionResult>): List<CompletionModel.CompletionResult>
}
