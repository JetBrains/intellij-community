// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.completion.ml.common

import com.intellij.codeInsight.completion.CompletionLocation
import com.intellij.codeInsight.completion.ml.ContextFeatures
import com.intellij.codeInsight.completion.ml.ElementFeatureProvider
import com.intellij.codeInsight.completion.ml.MLFeatureValue
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.openapi.util.Key
import com.intellij.textMatching.SimilarityScorer

class ContextSimilarityFeatures : ElementFeatureProvider {
  override fun getName(): String = "common_similarity"

  override fun calculateFeatures(element: LookupElement,
                                 location: CompletionLocation,
                                 contextFeatures: ContextFeatures): MutableMap<String, MLFeatureValue> {
    val features = mutableMapOf<String, MLFeatureValue>()
    features.setSimilarityFeatures("line", ContextSimilarityUtil.LINE_SIMILARITY_SCORER_KEY, element.lookupString, contextFeatures)
    features.setSimilarityFeatures("parent", ContextSimilarityUtil.PARENT_SIMILARITY_SCORER_KEY, element.lookupString, contextFeatures)
    return features
  }

  private fun MutableMap<String, MLFeatureValue>.setSimilarityFeatures(baseName: String,
                                                                       key: Key<SimilarityScorer>,
                                                                       lookupString: String,
                                                                       contextFeatures: ContextFeatures) {
    contextFeatures.getUserData(key)?.let { similarityScorer ->
      val scores = similarityScorer.score(lookupString)
      val avgScore = scores.average()
      val maxScore = scores.maxOrNull() ?: Double.NaN
      // TODO: remove when all models are updated
      addFeature("${baseName}_mean", avgScore)
      addFeature("${baseName}_max", maxScore)
      addFeature("${baseName}_full", maxScore)

      addFeature("${baseName}_stemmed_mean", avgScore)
      addFeature("${baseName}_stemmed_max", maxScore)
    }
  }

  private fun MutableMap<String, MLFeatureValue>.addFeature(name: String, value: Double) {
    if (!value.isNaN() && value != 0.0) this[name] = MLFeatureValue.float(value)
  }
}