// Copyright 2000-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.completion.ml.common

import com.intellij.codeInsight.completion.BaseCompletionService
import com.intellij.codeInsight.completion.CompletionLocation
import com.intellij.codeInsight.completion.ml.ContextFeatures
import com.intellij.codeInsight.completion.ml.ElementFeatureProvider
import com.intellij.codeInsight.completion.ml.MLFeatureValue
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.openapi.util.Key

class CommonElementLocationFeatures : ElementFeatureProvider {
  override fun getName(): String = "common"

  override fun calculateFeatures(
    element: LookupElement,
    location: CompletionLocation,
    contextFeatures: ContextFeatures
  ): MutableMap<String, MLFeatureValue> {

    val result = mutableMapOf<String, MLFeatureValue>()

    val completionElement = element.psiElement

    // ruby blocks tree access in tests - org.jetbrains.plugins.ruby.ruby.testCases.RubyCodeInsightTestFixture.complete
    if (completionElement?.language?.isKindOf("ruby") != true) {
      val linesDiff = LocationFeaturesUtil.linesDiff(location.completionParameters, completionElement)
      if (linesDiff != null) {
        result["lines_diff"] = MLFeatureValue.float(linesDiff)
      }
    }

    completionElement?.let {
      result["item_class"] = MLFeatureValue.className(it::class.java)
    }

    element.getUserData(BaseCompletionService.LOOKUP_ELEMENT_CONTRIBUTOR)?.let {
      val actualCompletionContributor: Class<*>? = element.getUserData(LOOKUP_ORIGINAL_ELEMENT_CONTRIBUTOR_TYPE)
      result["contributor"] = MLFeatureValue.className(actualCompletionContributor ?: it::class.java)
    }

    return result
  }

}

// For the TurboComplete plugin, to not confuse the ML model with new unknown contributors
val LOOKUP_ORIGINAL_ELEMENT_CONTRIBUTOR_TYPE = Key<Class<*>>("original contributor of the element")
