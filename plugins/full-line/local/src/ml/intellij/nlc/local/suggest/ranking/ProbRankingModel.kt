package ml.intellij.nlc.local.suggest.ranking

import ml.intellij.nlc.local.CompletionModel
import ml.intellij.nlc.local.suggest.feature.Features

/**
 * Naive version of [RankingModel] that performs reordering based on probability of completion
 * and match rank between it and prefix
 */
internal class ProbRankingModel : RankingModel {
    override fun rank(context: String, prefix: String, completions: List<CompletionModel.CompletionResult>): List<CompletionModel.CompletionResult> {
        val ranked = completions.map { completion ->
            completion to Features.stepProfit(completion.info)
        }.toMap()

        return ranked.entries.sortedByDescending { it.value }.map { it.key }
    }
}
