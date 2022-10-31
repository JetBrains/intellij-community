package ml.intellij.nlc.local.suggest.ranking

import ml.intellij.nlc.local.CompletionModel
import ml.intellij.nlc.local.suggest.feature.Features

class RawTotalProbRankingModel : RankingModel {
    override fun rank(
        context: String,
        prefix: String,
        completions: List<CompletionModel.CompletionResult>
    ): List<CompletionModel.CompletionResult> {
        val ranked = completions.associateWith { completion ->
            Features.prob(completion.info)
        }
        return ranked.entries.sortedByDescending { it.value }.map { it.key }
    }
}