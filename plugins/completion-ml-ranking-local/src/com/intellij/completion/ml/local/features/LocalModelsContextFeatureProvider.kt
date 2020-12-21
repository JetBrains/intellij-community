package com.intellij.completion.ml.local.features

import com.intellij.codeInsight.completion.ml.CompletionEnvironment
import com.intellij.codeInsight.completion.ml.ContextFeatureProvider
import com.intellij.codeInsight.completion.ml.MLFeatureValue
import com.intellij.completion.ml.local.models.LocalModelsManager
import org.jetbrains.annotations.NotNull

class LocalModelsContextFeatureProvider : ContextFeatureProvider {
  override fun getName(): String = "local"

  override fun calculateFeatures(environment: @NotNull CompletionEnvironment): @NotNull Map<String, MLFeatureValue> {
    val modelsManager = LocalModelsManager.getInstance(environment.parameters.position.project)
    val features = mutableMapOf<String, MLFeatureValue>()
    for (featuresProvider in modelsManager.modelFeatureProviders()) {
      features.putAll(featuresProvider.calculateContextFeatures(environment))
    }
    return features
  }
}