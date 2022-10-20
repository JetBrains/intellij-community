// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util

import com.intellij.featureStatistics.FeatureUsageTracker
import com.intellij.featureStatistics.ProductivityFeaturesRegistry
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project

@Service
internal class TipsOrderUtil {
  /**
   * Reorders tips to show the most useful ones in the beginning
   *
   * @return object that contains sorted tips and describes approach of how the tips are sorted
   */
  fun sort(tips: List<TipAndTrickBean>, project: Project): RecommendationDescription {
    val registry = ProductivityFeaturesRegistry.getInstance();
    if (registry == null) {
      thisLogger().warn("ProductivityFeaturesRegistry is not created")
      return RecommendationDescription(SHUFFLE_ALGORITHM, tips.shuffled(), "1")
    }

    FeatureUsageTracker.getInstance()  // instantiate just to load statistics of feature usage
    val tipsUsageManager = TipsUsageManager.getInstance()
    val allFeatures = registry.featureIds.map { registry.getFeatureDescriptor(it) }
    val tipInfoList = tips.map { tip ->
      val features = allFeatures.filter { it.tipId == tip.id }
      val lastTimeShown = tipsUsageManager.getLastTimeShown(tip.id)
      if (features.isNotEmpty()) {
        val isApplicable = features.any { feature ->
          val filters = registry.getMatchingFilters(feature.id)
          if (filters.isNotEmpty()) {
            filters.any { filter -> filter.isApplicable(feature.id, project) }
          }
          else true
        }
        val unusedScore = features.count { it.isUnused } * 1.0 / features.size
        TipInfo(tip, true, isApplicable, unusedScore, lastTimeShown)
      }
      else TipInfo(tip, false, true, 0.0, lastTimeShown)
    }

    val sortedTips = tipInfoList.sortedWith(getComparator()).map { it.tip }
    val adjustedSortedTips = tipsUsageManager.makeLastShownTipFirst(sortedTips)
    return RecommendationDescription(SORTING_ALGORITHM, adjustedSortedTips, SORTING_ALGORITHM_VERSION)
  }

  private fun getComparator(): Comparator<TipInfo> {
    return compareBy<TipInfo> { info -> info.featureFound && info.isApplicable && info.unusedScore > 0.1 }
      .then(compareBy { info -> !info.featureFound })
      .thenComparingDouble { info -> info.unusedScore }.reversed()
      .then(compareByDescending { info -> info.isApplicable })
      .thenComparingLong { info -> info.lastTimeShown }
  }

  private data class TipInfo(
    val tip: TipAndTrickBean,
    val featureFound: Boolean,
    val isApplicable: Boolean,
    val unusedScore: Double,
    val lastTimeShown: Long
  )

  companion object {
    const val SHUFFLE_ALGORITHM = "shuffle"
    const val SORTING_ALGORITHM = "usage_and_applicability"
    private const val SORTING_ALGORITHM_VERSION = "1"

    @JvmStatic
    fun getInstance(): TipsOrderUtil = service()
  }
}

internal data class RecommendationDescription(val algorithm: String, val tips: List<TipAndTrickBean>, val version: String?)

