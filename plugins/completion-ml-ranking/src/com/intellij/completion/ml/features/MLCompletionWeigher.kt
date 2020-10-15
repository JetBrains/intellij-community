// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.completion.ml.features

import com.intellij.codeInsight.completion.CompletionLocation
import com.intellij.codeInsight.completion.CompletionWeigher
import com.intellij.codeInsight.completion.ml.ElementFeatureProvider
import com.intellij.codeInsight.completion.ml.MLFeatureValue
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.completion.ml.storage.LookupStorage
import com.intellij.completion.ml.storage.MutableLookupStorage

class MLCompletionWeigher : CompletionWeigher() {
  override fun weigh(element: LookupElement, location: CompletionLocation): Comparable<*> {
    val storage: LookupStorage = LookupStorage.get(location.completionParameters) ?: return DummyComparable.EMPTY
    if (!storage.shouldComputeFeatures()) return DummyComparable.EMPTY
    val result = mutableMapOf<String, MLFeatureValue>()
    val contextFeatures = storage.contextProvidersResult()
    for (provider in ElementFeatureProvider.forLanguage(storage.language)) {
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

  private class DummyComparable(values: Map<String, MLFeatureValue>) : Comparable<Any> {
    val representation = calculateRepresentation(values)

    override fun compareTo(other: Any): Int = 0

    override fun toString(): String = representation

    companion object {
      val EMPTY = DummyComparable(emptyMap())

      private fun calculateRepresentation(values: Map<String, MLFeatureValue>): String {
        return values.entries.joinToString(",", "[", "]", transform = { "${it.key}=${MLFeaturesUtil.valueAsString(it.value)}" })
      }
    }
  }
}