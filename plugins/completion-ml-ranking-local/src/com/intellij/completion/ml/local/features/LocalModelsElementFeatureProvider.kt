package com.intellij.completion.ml.local.features

import com.intellij.codeInsight.completion.CompletionLocation
import com.intellij.codeInsight.completion.ml.ContextFeatures
import com.intellij.codeInsight.completion.ml.ElementFeatureProvider
import com.intellij.codeInsight.completion.ml.MLFeatureValue
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.completion.ml.local.models.LocalModelsManager

class LocalModelsElementFeatureProvider : ElementFeatureProvider {
  override fun getName(): String = "local"

  override fun calculateFeatures(element: LookupElement,
                                 location: CompletionLocation,
                                 contextFeatures: ContextFeatures): Map<String, MLFeatureValue> {
    val modelsManager = LocalModelsManager.getInstance(location.project)
    val features = mutableMapOf<String, MLFeatureValue>()
    for (featuresProvider in modelsManager.modelFeatureProviders()) {
      features.putAll(featuresProvider.calculateElementFeatures(element, contextFeatures))
    }
    return features
  }
}