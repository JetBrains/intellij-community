package com.intellij.completion.ml.experiments

import com.intellij.lang.Language
import java.util.concurrent.atomic.AtomicBoolean

class ExperimentStatusV2 : ExperimentStatus {
  val isDisabled: AtomicBoolean = AtomicBoolean(false)

  override fun forLanguage(language: Language): ExperimentInfo {
    if (isDisabled.get()) {
      return ExperimentInfo(false, 0, false)
    }
    
    return MLRankingExperimentFetcher.getInstance()?.getExperimentGroup(language)?.let {
      ExperimentInfo(
        inExperiment = true,
        version = it.id,
        shouldRank = it.useMLRanking ?: false,
        shouldShowArrows = it.showArrows ?: false,
        shouldCalculateFeatures = it.calculateFeatures ?: false,
        shouldLogElementFeatures = it.logElementFeatures ?: true,
      )
    } ?: ExperimentInfo(false, MLRankingExperiment.NO_EXP)
  }

  override fun isDisabled(): Boolean = isDisabled.get()
  override fun disable() {
    isDisabled.set(true)
  }
}