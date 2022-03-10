package com.jetbrains.completion.ml.ranker

import com.intellij.completion.ml.ranker.ExperimentModelProvider
import com.intellij.internal.ml.catboost.CatBoostJarCompletionModelProvider
import com.intellij.lang.Language

class ExperimentJavaRecommendersMLRankingProvider : CatBoostJarCompletionModelProvider(
  CompletionRankingModelsBundle.message("ml.completion.experiment.model.java"), "java_features_exp_rec",
  "java_model_exp_rec"), ExperimentModelProvider {

  override fun isLanguageSupported(language: Language): Boolean = language.id.compareTo("java", ignoreCase = true) == 0

  override fun experimentGroupNumber(): Int = 14
}
