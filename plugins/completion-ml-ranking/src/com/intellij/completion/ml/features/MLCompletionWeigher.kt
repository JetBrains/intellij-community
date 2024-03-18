// Copyright 2000-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.completion.ml.features

import com.intellij.codeInsight.completion.CompletionLocation
import com.intellij.codeInsight.completion.CompletionWeigher
import com.intellij.codeInsight.completion.ml.ElementFeatureProvider
import com.intellij.codeInsight.completion.ml.MLFeatureValue
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.completion.ml.storage.LookupStorage
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.ProcessCanceledException

internal class MLCompletionWeigher : CompletionWeigher() {
  override fun weigh(element: LookupElement, location: CompletionLocation): Comparable<*> {
    val storage = LookupStorage.get(location.completionParameters) ?: return DummyComparable.EMPTY
    if (!storage.shouldComputeFeatures()) {
      return DummyComparable.EMPTY
    }

    val result = mutableMapOf<String, MLFeatureValue>()
    val contextFeatures = storage.contextProvidersResult()
    for (provider in ElementFeatureProvider.forLanguage(storage.language)) {
      try {
        val name = provider.name

        val features = storage.performanceTracker.trackElementFeaturesCalculation(name) {
          provider.calculateFeatures(element, location, contextFeatures)
        }

        for ((featureName, featureValue) in features) {
          result["${name}_$featureName"] = featureValue
        }
      }
      catch (e: ProcessCanceledException) {
        throw e
      }
      catch (e: Throwable) {
        thisLogger().error(e)
      }
    }

    return if (result.isEmpty()) DummyComparable.EMPTY else DummyComparable(result)
  }

  internal class DummyComparable(values: Map<String, MLFeatureValue>) : Comparable<Any> {
    val mlFeatures = values.mapValues { MLFeaturesUtil.getRawValue(it.value) }

    companion object {
      @JvmField
      val EMPTY = DummyComparable(emptyMap())
    }

    override fun compareTo(other: Any): Int = 0

    override fun toString(): String {
      return mlFeatures.entries.joinToString(separator = ",", prefix = "[", postfix = "]", transform = { "${it.key}=${it.value}" })
    }
  }
}
