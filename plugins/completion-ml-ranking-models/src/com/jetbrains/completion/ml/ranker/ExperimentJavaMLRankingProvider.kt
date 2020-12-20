package com.jetbrains.completion.ml.ranker

import com.intellij.completion.ml.ranker.ExperimentModelProvider
import com.intellij.lang.Language
import com.jetbrains.completion.ml.ranker.cb.JarCatBoostCompletionModelProvider
import com.jetbrains.completion.ml.ranker.cb.jvm.NaiveCatBoostJarCompletionModelProvider

class ExperimentJavaMLRankingProvider: JarCatBoostCompletionModelProvider(
  CompletionRankingModelsBundle.message("ml.completion.experiment.model.java"), "java_features", "java_model"), ExperimentModelProvider {

  override fun isLanguageSupported(language: Language): Boolean = language.id.compareTo("Java", ignoreCase = true) == 0

  override fun experimentGroupNumber(): Int = 13
}

class ExperimentJavaMLRankingProvider2: NaiveCatBoostJarCompletionModelProvider(
  CompletionRankingModelsBundle.message("ml.completion.experiment.model.java"), "java_features2", "java_model2"), ExperimentModelProvider {

  override fun isLanguageSupported(language: Language): Boolean = language.id.compareTo("Java", ignoreCase = true) == 0

  override fun experimentGroupNumber(): Int = 14
}