// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.completion.ml.sorting

class RecommendersLookupFeatureProvider : LookupFeatureProvider {
  override fun calculateFeatures(elementFeaturesList: MutableList<ElementFeatures>): MutableMap<String, String> {
    val isRecommendersAvailable = elementFeaturesList.any { features ->
      REC_FEATURES_NAMES.any { name -> features.additional.contains(name) }
    }

    return mutableMapOf(
      REC_AVAILABILITY_FEATURE to if (isRecommendersAvailable) "1" else "0"
    )
  }

  companion object {
    private const val REC_AVAILABILITY_FEATURE = "ml_is_recommenders_available"
    private val REC_FEATURES_NAMES: List<String> = listOf("ml_rec-instances_probability", "ml_rec-statics2_probability")
  }
}