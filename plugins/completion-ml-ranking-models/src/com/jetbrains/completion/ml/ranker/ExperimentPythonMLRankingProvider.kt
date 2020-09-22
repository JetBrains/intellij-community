package com.jetbrains.completion.ml.ranker

import com.intellij.completion.ml.ranker.ExperimentModelProvider
import com.intellij.internal.ml.DecisionFunction
import com.intellij.internal.ml.ModelMetadata
import com.intellij.internal.ml.completion.CompletionRankingModelBase
import com.intellij.internal.ml.completion.JarCompletionModelProvider
import com.jetbrains.completion.ranker.model.python.MLGlassBox
import com.intellij.lang.Language

class ExperimentPythonMLRankingProvider: JarCompletionModelProvider(
  CompletionRankingModelsBundle.message("ml.completion.experiment.model.python"), "python_features"), ExperimentModelProvider {

  override fun createModel(metadata: ModelMetadata): DecisionFunction {
    return object : CompletionRankingModelBase(metadata) {
      override fun predict(features: DoubleArray?): Double = MLGlassBox.makePredict(features)
    }
  }

  override fun isLanguageSupported(language: Language): Boolean = language.id.compareTo("Python", ignoreCase = true) == 0

  override fun experimentGroupNumber(): Int = 13
}