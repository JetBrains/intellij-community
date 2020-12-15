package com.intellij.completion.ml.local.features

import com.intellij.codeInsight.completion.ml.CompletionEnvironment
import com.intellij.codeInsight.completion.ml.ContextFeatureProvider
import com.intellij.codeInsight.completion.ml.MLFeatureValue
import com.intellij.completion.ml.local.models.frequency.FrequencyLocalModel
import org.jetbrains.annotations.NotNull

class LocalModelsContextFeatureProvider : ContextFeatureProvider {
  override fun getName(): String = "local_models"

  override fun calculateFeatures(environment: @NotNull CompletionEnvironment): @NotNull Map<String, MLFeatureValue> {
    return FrequencyLocalModel.getInstance(environment.parameters.position.project).calculateContextFeatures(environment)
  }
}