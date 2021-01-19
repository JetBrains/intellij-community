package com.jetbrains.completion.ml.ranker

import com.intellij.completion.ml.ranker.ExperimentModelProvider
import com.intellij.internal.ml.catboost.CatBoostJarCompletionModelProvider
import com.intellij.lang.Language

class ExperimentPythonMLRankingProvider : CatBoostJarCompletionModelProvider(
  CompletionRankingModelsBundle.message("ml.completion.experiment.model.python"), "python_features_exp", "python_model_exp"), ExperimentModelProvider {

  override fun isLanguageSupported(language: Language): Boolean = language.id.compareTo("Python", ignoreCase = true) == 0

  override fun experimentGroupNumber(): Int = 13
}