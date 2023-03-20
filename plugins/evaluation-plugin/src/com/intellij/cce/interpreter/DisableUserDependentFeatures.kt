package com.intellij.cce.interpreter

import com.intellij.completion.ml.features.RankingFeaturesOverrides

class DisableUserDependentFeatures : RankingFeaturesOverrides {
  companion object {
    private val mlUserDependentFeaturesToDefaultValues = mapOf(
      "ml_ngram_recent_files" to "0.0",
      "ml_recent_places_children_contains" to "0",
      "ml_recent_places_contains" to "0",
      "ml_vcs_declaration_is_changed" to "0",
    )

    private val userDependentWeighersToDefaultValues = mapOf(
      "stats" to "0.0"
    )
  }

  override fun getMlElementFeaturesOverrides(features: Map<String, Any>): Map<String, Any> {
    val overrides = mutableMapOf<String, Any>()
    for ((name, default) in mlUserDependentFeaturesToDefaultValues) {
      if (!features.containsKey(name)) continue
      overrides[name] = default
    }
    return overrides
  }
}