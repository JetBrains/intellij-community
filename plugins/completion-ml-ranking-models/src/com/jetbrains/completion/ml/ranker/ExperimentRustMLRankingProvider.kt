// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.completion.ml.ranker

import com.intellij.completion.ml.ranker.ExperimentModelProvider
import com.intellij.internal.ml.DecisionFunction
import com.intellij.internal.ml.ModelMetadata
import com.intellij.internal.ml.completion.CompletionRankingModelBase
import com.intellij.internal.ml.completion.JarCompletionModelProvider
import com.intellij.lang.Language
import com.jetbrains.completion.ranker.model.rust.MLGlassBox

class ExperimentRustMLRankingProvider : JarCompletionModelProvider(
  CompletionRankingModelsBundle.message("ml.completion.experiment.model.rust"), "rust_features"), ExperimentModelProvider {
  override fun createModel(metadata: ModelMetadata): DecisionFunction {
    return object : CompletionRankingModelBase(metadata) {
      override fun predict(features: DoubleArray?): Double = MLGlassBox.makePredict(features)
    }
  }

  override fun isLanguageSupported(language: Language): Boolean = language.id.compareTo("Rust", ignoreCase = true) == 0

  override fun experimentGroupNumber(): Int = 13
}
