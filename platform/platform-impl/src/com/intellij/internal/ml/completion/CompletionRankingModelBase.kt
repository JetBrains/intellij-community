// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.ml.completion

import com.intellij.internal.ml.DecisionFunction
import com.intellij.internal.ml.FeatureMapper
import com.intellij.internal.ml.ModelMetadata


abstract class CompletionRankingModelBase(private val metadata: ModelMetadata) : DecisionFunction {
  private val isPositionBased = metadata.checkIfPositionFeatureUsed()

  override fun getFeaturesOrder(): Array<FeatureMapper> {
    return metadata.featuresOrder
  }

  override fun version(): String? {
    return metadata.version
  }

  override fun getRequiredFeatures(): List<String> = emptyList()

  override fun getUnknownFeatures(features: Collection<String>): List<String> {
    if (!isPositionBased) return emptyList()
    var unknownFeatures: MutableList<String>? = null
    for (featureName in features) {
      if (featureName !in metadata.knownFeatures) {
        if (unknownFeatures == null) {
          unknownFeatures = mutableListOf()
        }
        unknownFeatures.add(featureName)
      }
    }

    return unknownFeatures ?: emptyList()
  }

  private companion object {
    private fun ModelMetadata.checkIfPositionFeatureUsed(): Boolean {
      return featuresOrder.any { it.featureName == "position" || it.featureName == "relative_position" }
    }
  }
}
