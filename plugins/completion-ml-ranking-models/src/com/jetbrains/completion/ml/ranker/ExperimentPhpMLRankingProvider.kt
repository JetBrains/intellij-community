package com.jetbrains.completion.ml.ranker

import com.intellij.completion.ml.ranker.ExperimentModelProvider
import com.intellij.internal.ml.catboost.CatBoostJarCompletionModelProvider
import com.intellij.internal.ml.completion.DecoratingItemsPolicy
import com.intellij.lang.Language

class ExperimentPhpMLRankingProvider : CatBoostJarCompletionModelProvider(
  CompletionRankingModelsBundle.message("ml.completion.experiment.model.php"), "php_features_exp", "php_model_exp"), ExperimentModelProvider {

  override fun isLanguageSupported(language: Language): Boolean = language.id.compareTo("PHP", ignoreCase = true) == 0

  override fun experimentGroupNumber(): Int = 13

  override fun getDecoratingPolicy(): DecoratingItemsPolicy = DecoratingItemsPolicy.Composite(
    DecoratingItemsPolicy.ByAbsoluteThreshold(4.0),
    DecoratingItemsPolicy.ByRelativeThreshold(2.5)
  )
}