package ml.intellij.nlc.local.suggest.filtering

import ml.intellij.nlc.local.CompletionConfig
import ml.intellij.nlc.local.CompletionModel
import ml.intellij.nlc.local.suggest.feature.Features
import kotlin.math.ln

/**
 * Naive version of filtering that uses probability of completion from completion model and
 * and filters out variants with too small completion length or non-symbol starting completions
 */
internal class ProbFilterModel : FilterModel<CompletionConfig.Filter> {
  override fun filter(
    context: String,
    prefix: String,
    completions: List<CompletionModel.CompletionResult>,
    config: CompletionConfig.Filter
  ): List<CompletionModel.CompletionResult> {
    return completions.filter { completion ->
      val prob = Features.prob(completion.info)
      val meanProb = Features.meanProb(completion.info)
      val startFromWord = completion.text[0] == ' ' && completion.text[1].isLetter()
      val symbolLen = completion.text.length - prefix.length

      ln(meanProb) >= config.minAvgLogProb
      && prob >= config.minProb
      && symbolLen >= config.minSymbolLen
      && startFromWord
    }
  }
}
