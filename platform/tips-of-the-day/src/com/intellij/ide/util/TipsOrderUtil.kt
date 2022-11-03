// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util

import com.intellij.featureStatistics.FeatureDescriptor
import com.intellij.featureStatistics.FeatureUsageTracker
import com.intellij.featureStatistics.ProductivityFeaturesRegistry
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.util.text.DateFormatUtil

@Service
internal class TipsOrderUtil {
  /**
   * Reorders tips to show the most useful ones in the beginning
   *
   * @return object that contains sorted tips and describes approach of how the tips are sorted
   */
  fun sort(tips: List<TipAndTrickBean>, project: Project): TipsSortingResult {
    val registry = ProductivityFeaturesRegistry.getInstance();
    if (registry == null) {
      thisLogger().warn("ProductivityFeaturesRegistry is not created")
      return TipsSortingResult(tips.shuffled(), SHUFFLE_ALGORITHM, "1")
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
        val unusedFeatures = features.filter(FeatureDescriptor::isUnused)
        val unusedScore = unusedFeatures.size * 1.0 / features.size
        val utilityScore = if (unusedFeatures.isNotEmpty()) {
          unusedFeatures.maxOf(FeatureDescriptor::getUtilityScore)
        }
        else features.maxOf(FeatureDescriptor::getUtilityScore)
        TipInfo(tip, true, isApplicable, unusedScore, utilityScore, lastTimeShown)
      }
      else TipInfo(tip, false, true, 0.0, 3, lastTimeShown)
    }

    val sortedTips = tipInfoList.sortedWith(getComparator()).map { it.tip }
    val adjustedSortedTips = adjustFirstTip(sortedTips)
    return TipsSortingResult(adjustedSortedTips, SORTING_ALGORITHM, SORTING_ALGORITHM_VERSION)
  }

  private fun getComparator(): Comparator<TipInfo> {
    return compareBy<TipInfo> { info -> info.featureFound && info.isApplicable && info.unusedScore > 0.1 }
      .then(compareBy { info -> !info.featureFound })
      .thenComparingDouble { info -> info.unusedScore }
      .then(compareBy { info -> info.isApplicable })
      .thenComparingInt { info -> info.utilityScore }.reversed()
      .thenComparingLong { info -> info.lastTimeShown }
  }

  private fun adjustFirstTip(tips: List<TipAndTrickBean>): List<TipAndTrickBean> {
    val tipsUsageManager = TipsUsageManager.getInstance()
    if (tipsUsageManager.wereTipsShownToday()) {
      return tipsUsageManager.makeLastShownTipFirst(tips)
    }
    else {
      val index = tips.indexOfFirst { tip ->
        System.currentTimeMillis() - tipsUsageManager.getLastTimeShown(tip.id) > MIN_SUCCESSIVE_SHOW_INTERVAL_DAYS * DateFormatUtil.DAY
      }
      if (index <= 0) {
        return tips
      }
      else {
        val mutableTips = tips.toMutableList()
        val newFirstTip = mutableTips.removeAt(index)
        mutableTips.add(0, newFirstTip)
        return mutableTips
      }
    }
  }

  private data class TipInfo(
    val tip: TipAndTrickBean,
    val featureFound: Boolean,
    val isApplicable: Boolean,
    val unusedScore: Double,
    val utilityScore: Int,
    val lastTimeShown: Long
  )

  companion object {
    const val SHUFFLE_ALGORITHM = "shuffle"
    const val SORTING_ALGORITHM = "usage_and_applicability"
    private const val SORTING_ALGORITHM_VERSION = "1"

    // Minimum time between showing the same tip at the first place
    private const val MIN_SUCCESSIVE_SHOW_INTERVAL_DAYS = 5

    @JvmStatic
    fun getInstance(): TipsOrderUtil = service()
  }
}

internal data class TipsSortingResult @JvmOverloads constructor(val tips: List<TipAndTrickBean>,
                                                                val algorithm: String = "unknown",
                                                                val version: String? = null)

