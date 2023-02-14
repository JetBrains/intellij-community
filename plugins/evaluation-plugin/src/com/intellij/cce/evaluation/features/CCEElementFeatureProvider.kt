package com.intellij.cce.evaluation.features

import com.intellij.codeInsight.completion.CompletionLocation
import com.intellij.codeInsight.completion.ml.ContextFeatures
import com.intellij.codeInsight.completion.ml.ElementFeatureProvider
import com.intellij.codeInsight.completion.ml.MLFeatureValue
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.completion.ml.util.idString

class CCEElementFeatureProvider : ElementFeatureProvider {
  override fun getName(): String = "cce"

  override fun calculateFeatures(element: LookupElement,
                                 location: CompletionLocation,
                                 contextFeatures: ContextFeatures): Map<String, MLFeatureValue> {
    return mapOf(
      "suggestion_text" to createStringFeature(element.idString())
    )
  }
}