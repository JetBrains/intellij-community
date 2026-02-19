// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util

import com.intellij.featureStatistics.FeatureDescriptor
import com.intellij.featureStatistics.FeatureUsageTracker
import com.intellij.featureStatistics.ProductivityFeaturesRegistry
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import kotlin.random.Random
import kotlin.time.Duration.Companion.days

@Service
internal class TipOrderUtil {
  /**
   * Reorders tips to show the most useful ones in the beginning.
   * If a provided project is null, tip applicability will not be taken into account.
   * All tips will be counted as applicable.
   *
   * @return object that contains sorted tips and describes approach of how the tips are sorted
   */
  fun sort(tips: List<TipAndTrickBean>, project: Project?): TipsSortingResult {
    val registry = ProductivityFeaturesRegistry.getInstance()
    if (registry == null) {
      thisLogger().warn("ProductivityFeaturesRegistry is not created")
      return TipsSortingResult.create(tips.shuffled(), SHUFFLE_ALGORITHM, "1")
    }

    FeatureUsageTracker.getInstance()  // instantiate just to load statistics of feature usage
    val allFeatures = registry.featureIds.map { registry.getFeatureDescriptor(it) }
    val random = Random(TipsUsageManager.getInstance().tipsOrderSeed)
    val tipInfoList = tips.shuffled(random).map { tip ->
      val features = allFeatures.filter { it.tipId == tip.id }
      if (features.isNotEmpty()) {
        val isApplicable = if (project == null) {
          true
        }
        else features.any { feature ->
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
        TipInfo(tip, true, isApplicable, unusedScore, utilityScore)
      }
      else TipInfo(tip, false, true, 0.0, 3)
    }

    val sortedTips = tipInfoList.sortedWith(getComparator()).map { it.tip }
    val adjustedSortedTips = adjustFirstTip(sortedTips)
    return TipsSortingResult.create(adjustedSortedTips, SORTING_ALGORITHM, SORTING_ALGORITHM_VERSION)
  }

  private fun getComparator(): Comparator<TipInfo> {
    return compareBy<TipInfo> { info -> info.featureFound && info.isApplicable && info.unusedScore > 0.1 }
      .then(compareBy { info -> !info.featureFound })
      .thenComparingDouble { info -> info.unusedScore }
      .then(compareBy { info -> info.isApplicable })
      .thenComparingInt { info -> info.utilityScore }.reversed()
  }

  private fun adjustFirstTip(tips: List<TipAndTrickBean>): List<TipAndTrickBean> {
    if (tips.isEmpty()) {
      return emptyList()
    }
    val tipsUsageManager = TipsUsageManager.getInstance()
    return if (tipsUsageManager.wereTipsShownToday()) {
      val lastShownTipId = tips.maxByOrNull { tipsUsageManager.getLastTimeShown(it.id) }?.id ?: tips[0]
      val lastShownTipIndex = tips.indexOfFirst { it.id == lastShownTipId }
      cycleShift(tips, lastShownTipIndex)
    }
    else {
      val indexToShowFirst = tips.indexOfFirst { tip ->
        System.currentTimeMillis() - tipsUsageManager.getLastTimeProposed(tip.id) > MIN_SUCCESSIVE_SHOW_INTERVAL_MS
      }
      if (indexToShowFirst <= 0) tips else cycleShift(tips, indexToShowFirst)
    }
  }

  /** Move [0; value) elements to the end of the list, so this list will start from an element with index [value]
   *  Can be implemented in place, without allocation of the new list, but is not required here.
   */
  private fun cycleShift(tips: List<TipAndTrickBean>, value: Int): List<TipAndTrickBean> {
    val before = tips.subList(0, value)
    val after = tips.subList(value, tips.size)
    return after + before
  }

  private data class TipInfo(
    val tip: TipAndTrickBean,
    val featureFound: Boolean,
    val isApplicable: Boolean,
    val unusedScore: Double,
    val utilityScore: Int
  )

  companion object {
    const val SHUFFLE_ALGORITHM = "shuffle"
    const val SORTING_ALGORITHM = "usage_and_applicability"
    private const val SORTING_ALGORITHM_VERSION = "2"

    // Minimum time between showing the same tip in the first place
    private val MIN_SUCCESSIVE_SHOW_INTERVAL_MS = 14.days.inWholeMilliseconds

    @JvmStatic
    fun getInstance(): TipOrderUtil = service()
  }
}
