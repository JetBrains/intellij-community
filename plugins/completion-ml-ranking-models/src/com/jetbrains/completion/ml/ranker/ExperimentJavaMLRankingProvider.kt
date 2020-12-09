package com.jetbrains.completion.ml.ranker

import com.intellij.completion.ml.ranker.ExperimentModelProvider
import com.intellij.lang.Language
import com.jetbrains.completion.ml.ranker.cb.JarCatBoostCompletionModelProvider

class ExperimentJavaMLRankingProvider: JarCatBoostCompletionModelProvider(
  CompletionRankingModelsBundle.message("ml.completion.experiment.model.java"), "java_features", "java_model"), ExperimentModelProvider {

  override fun isLanguageSupported(language: Language): Boolean = language.id.compareTo("Java", ignoreCase = true) == 0

  override fun experimentGroupNumber(): Int = 13
}