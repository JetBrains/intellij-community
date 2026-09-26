package com.intellij.completion.ml.features

import com.intellij.lang.Language
import com.intellij.lang.LanguageExtension
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface RankingFeaturesOverrides {
  companion object {
    private val EP_NAME = LanguageExtension<RankingFeaturesOverrides>("com.intellij.completion.ml.featuresOverride")

    fun forLanguage(language: Language): List<RankingFeaturesOverrides> {
      return EP_NAME.allForLanguageOrAny(language)
    }
  }

  fun getMlElementFeaturesOverrides(features: Map<String, Any>): Map<String, Any>

  fun getDefaultWeigherFeaturesOverrides(features: Map<String, Any>): Map<String, Any> {
    return emptyMap()
  }
}