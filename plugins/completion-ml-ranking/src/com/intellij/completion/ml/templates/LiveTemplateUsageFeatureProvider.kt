package com.intellij.completion.ml.templates

import com.intellij.codeInsight.completion.CompletionLocation
import com.intellij.codeInsight.completion.ml.ContextFeatures
import com.intellij.codeInsight.completion.ml.ElementFeatureProvider
import com.intellij.codeInsight.completion.ml.MLFeatureValue
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.completion.ml.util.isLiveTemplate

/**
 * `previously_used` relies on locally stored list of used templates that were completed for the user
 * `recent_use_count` accounts for the templates usage during the current app launch only
 */
class LiveTemplateUsageFeatureProvider : ElementFeatureProvider {
  override fun getName(): String = "template"

  override fun calculateFeatures(element: LookupElement,
                                 location: CompletionLocation,
                                 contextFeatures: ContextFeatures): MutableMap<String, MLFeatureValue> {
    val result = mutableMapOf<String, MLFeatureValue>()
    if (element.isLiveTemplate()) {
      LiveTemplateUsageTracker.getInstance().getRecentUseCount(element.lookupString).let {
        result["previously_used"] = MLFeatureValue.binary(it != null)
        result["recent_use_count"] = MLFeatureValue.float(it ?: 0)
      }
    }
    return result
  }
}