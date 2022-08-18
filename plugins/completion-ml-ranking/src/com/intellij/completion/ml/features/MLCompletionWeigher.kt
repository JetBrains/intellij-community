// Copyright 2000-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.completion.ml.features

import com.intellij.codeInsight.completion.CompletionLocation
import com.intellij.codeInsight.completion.CompletionWeigher
import com.intellij.codeInsight.completion.ml.ElementFeatureProvider
import com.intellij.codeInsight.completion.ml.MLFeatureValue
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.completion.ml.storage.LookupStorage
import com.intellij.openapi.extensions.impl.ExtensionProcessingHelper

class MLCompletionWeigher : CompletionWeigher() {
  override fun weigh(element: LookupElement, location: CompletionLocation): Comparable<*> {
    val storage: LookupStorage = LookupStorage.get(location.completionParameters) ?: return DummyComparable.EMPTY
    if (!storage.shouldComputeFeatures()) return DummyComparable.EMPTY
    val result = mutableMapOf<String, MLFeatureValue>()
    val contextFeatures = storage.contextProvidersResult()
    ExtensionProcessingHelper.forEachExtensionSafe(ElementFeatureProvider.forLanguage(storage.language)) { provider ->
      val name = provider.name

      val features = storage.performanceTracker.trackElementFeaturesCalculation(name) {
        provider.calculateFeatures(element, location, contextFeatures)
      }

      for ((featureName, featureValue) in features) {
        result["${name}_$featureName"] = featureValue
      }
    }

    return if (result.isEmpty()) DummyComparable.EMPTY else DummyComparable(result)
  }

  internal class DummyComparable(values: Map<String, MLFeatureValue>) : Comparable<Any> {
    val mlFeatures = values.mapValues { MLFeaturesUtil.getRawValue(it.value) }

    override fun compareTo(other: Any): Int = 0

    override fun toString(): String {
      return mlFeatures.entries.joinToString(",", "[", "]", transform = { "${it.key}=${it.value}" })
    }

    companion object {
      val EMPTY = DummyComparable(emptyMap())
    }
  }
}
