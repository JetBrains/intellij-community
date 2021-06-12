// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.completion.ml.common

import com.intellij.codeInsight.completion.CompletionLocation
import com.intellij.codeInsight.completion.ml.ContextFeatures
import com.intellij.codeInsight.completion.ml.ElementFeatureProvider
import com.intellij.codeInsight.completion.ml.MLFeatureValue
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.openapi.util.Key

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
                                                                       key: Key<ContextSimilarityUtil.ContextSimilarityScoringFunction>,
                                                                       lookupString: String,
                                                                       contextFeatures: ContextFeatures) {
    val similarityScorer = contextFeatures.getUserData(key)
    if (similarityScorer != null) {
      val prefixSimilarity = similarityScorer.scorePrefixSimilarity(lookupString)
      val stemmedSimilarity = similarityScorer.scoreStemmedSimilarity(lookupString)
      addFeature("${baseName}_mean", prefixSimilarity.meanSimilarity())
      addFeature("${baseName}_max", prefixSimilarity.maxSimilarity())
      addFeature("${baseName}_full", prefixSimilarity.fullSimilarity())
      addFeature("${baseName}_stemmed_mean", stemmedSimilarity.meanSimilarity())
      addFeature("${baseName}_stemmed_max", stemmedSimilarity.maxSimilarity())
    }
  }

  private fun MutableMap<String, MLFeatureValue>.addFeature(name: String, value: Double) {
    if (value != 0.0) this[name] = MLFeatureValue.float(value)
  }
}