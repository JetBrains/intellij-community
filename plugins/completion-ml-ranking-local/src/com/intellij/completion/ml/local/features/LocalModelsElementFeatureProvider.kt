package com.intellij.completion.ml.local.features

import com.intellij.codeInsight.completion.CompletionLocation
import com.intellij.codeInsight.completion.ml.ContextFeatures
import com.intellij.codeInsight.completion.ml.ElementFeatureProvider
import com.intellij.codeInsight.completion.ml.MLFeatureValue
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.completion.ml.local.models.frequency.FrequencyLocalModel

class LocalModelsElementFeatureProvider : ElementFeatureProvider {
  override fun getName(): String = "local_models"

  override fun calculateFeatures(element: LookupElement,
                                 location: CompletionLocation,
                                 contextFeatures: ContextFeatures): Map<String, MLFeatureValue> {
    return FrequencyLocalModel.getInstance(location.project).calculateElementFeatures(element, contextFeatures)
  }
}