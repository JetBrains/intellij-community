package com.jetbrains.completion.ml.ranker

import com.intellij.completion.ml.ranker.ExperimentModelProvider
import com.intellij.internal.ml.catboost.CatBoostJarCompletionModelProvider
import com.intellij.lang.Language

class ExperimentalCSSMLRankingProvider : CatBoostJarCompletionModelProvider(
  CompletionRankingModelsBundle.message("ml.completion.experiment.model.css"), "css_features", "css_model"), ExperimentModelProvider {

  override fun isLanguageSupported(language: Language): Boolean = language.id.compareTo("css", ignoreCase = true) == 0

  override fun experimentGroupNumber(): Int = 13
}
