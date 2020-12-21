package com.intellij.completion.ml.local.models.api

import com.intellij.codeInsight.completion.ml.CompletionEnvironment
import com.intellij.codeInsight.completion.ml.ContextFeatures
import com.intellij.codeInsight.completion.ml.MLFeatureValue
import com.intellij.codeInsight.lookup.LookupElement

interface LocalModelFeaturesProvider {
  fun calculateContextFeatures(environment: CompletionEnvironment): Map<String, MLFeatureValue>
  fun calculateElementFeatures(element: LookupElement, contextFeatures: ContextFeatures): Map<String, MLFeatureValue>
}