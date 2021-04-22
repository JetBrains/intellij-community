// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util

import com.intellij.featureStatistics.FeatureDescriptor
import com.intellij.featureStatistics.FeaturesRegistryListener
import com.intellij.featureStatistics.ProductivityFeaturesRegistry
import com.intellij.ide.TipsOfTheDayUsagesCollector
import com.intellij.ide.util.TipAndTrickBean.findByFileName
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.intellij.openapi.diagnostic.logger
import com.intellij.util.text.DateFormatUtil
import org.jetbrains.annotations.ApiStatus
import kotlin.random.Random

@ApiStatus.Internal
@State(name = "ShownTips", storages = [Storage(value = "shownTips.xml", roamingType = RoamingType.DISABLED)])
internal class TipsUsageManager : PersistentStateComponent<TipsUsageManager.State> {
  companion object {
    @JvmStatic
    fun getInstance(): TipsUsageManager = service()

    const val BY_TIP_UTILITY = "tip_utility"
  }

  private val shownTips: MutableMap<String, Long> = mutableMapOf()

  init {
    ApplicationManager.getApplication().messageBus.connect().subscribe(FeaturesRegistryListener.TOPIC, TipsUsageListener())
  }

  override fun getState(): State = State(shownTips)

  override fun loadState(state: State) {
    shownTips.putAll(state.shownTips)
  }

  fun sortByUtility(tips: List<TipAndTrickBean>): RecommendationDescription {
    val usedTips = mutableSetOf<TipAndTrickBean>()
    val unusedTips = mutableSetOf<TipAndTrickBean>()
    ProductivityFeaturesRegistry.getInstance()?.let { featuresRegistry ->
      for (featureId in featuresRegistry.featureIds) {
        val feature = featuresRegistry.getFeatureDescriptor(featureId)
        val tip = TipUIUtil.getTip(feature) ?: continue
        if (System.currentTimeMillis() - feature.lastTimeUsed < DateFormatUtil.MONTH) {
          usedTips.add(tip)
          unusedTips.remove(tip) // some tips correspond to multiple features
        } else if (!usedTips.contains(tip)) {
          unusedTips.add(tip)
        }
      }
    }
    val otherTips = tips.toSet() - (usedTips + unusedTips)
    val sortedByUtility = TipsUtilityHolder.INSTANCE.sampleTips(unusedTips)
    return RecommendationDescription(BY_TIP_UTILITY,
                                     sortedByUtility + otherTips.shuffled() + usedTips.shuffled(),
                                     TipsUtilityHolder.INSTANCE.getMetadataVersion())
  }

  fun fireTipShown(tip: TipAndTrickBean) {
    shownTips[tip.fileName] = System.currentTimeMillis()
  }

  fun filterShownTips(tips: List<TipAndTrickBean>): List<TipAndTrickBean> {
    val resultTips = tips.toMutableList()
    for (tipFile in shownTips.keys.shuffled()) {
      resultTips.find { it.fileName == tipFile }?.let { tip ->
        resultTips.remove(tip)
        resultTips.add(tip)
      }
    }
    if (wereTipsShownToday()) {
      shownTips.maxByOrNull { it.value }?.let {
        findByFileName(it.key)?.let { tip ->
          resultTips.remove(tip)
          resultTips.add(0, tip)
        }
      }
    }
    return resultTips
  }

  fun wereTipsShownToday(): Boolean = System.currentTimeMillis() - (shownTips.maxOfOrNull { it.value } ?: 0) < DateFormatUtil.DAY

  data class State(var shownTips: Map<String, Long> = emptyMap())

  private class TipsUtilityHolder private constructor() {
    companion object {
      val INSTANCE: TipsUtilityHolder = TipsUtilityHolder()

      private val LOG = logger<TipsUtilityHolder>()
      private const val TIPS_UTILITY_FILE = "tips_utility.csv"
    }

    private val tips2utility: Map<String, Double> = readUtilities()

    fun sampleTips(tips: Iterable<TipAndTrickBean>): List<TipAndTrickBean> {
      val (knownTips, unknownTips) = tips.map { Pair(it, getTipUtility(it)) }.partition { it.second > 0 }
      val sortedTips = knownTips.sortedByDescending { it.second }.toMutableSet()
      val result = mutableListOf<TipAndTrickBean>()
      var totalUtility = sortedTips.sumByDouble { it.second }
      for (i in 0 until sortedTips.size) {
        var cumulativeUtility = 0.0
        if (totalUtility <= 0.0) {
          result.addAll(sortedTips.map { it.first })
          break
        }
        val prob = Random.nextDouble(totalUtility)
        for (tip2utility in sortedTips) {
          val tipUtility = tip2utility.second
          cumulativeUtility += tipUtility
          if (prob <= cumulativeUtility) {
            result.add(tip2utility.first)
            totalUtility -= tipUtility
            sortedTips.remove(tip2utility)
            break
          }
        }
      }
      return result + unknownTips.map { it.first }.shuffled()
    }

    fun getMetadataVersion(): String = "0.1"

    private fun readUtilities() : Map<String, Double> {
      val classLoader = TipsUtilityHolder::class.java.classLoader
      val lines = classLoader.getResourceAsStream(TIPS_UTILITY_FILE)?.use { it.bufferedReader().readLines() }
      if (lines == null) {
        LOG.error("Can't read resource file with tips utilities: $TIPS_UTILITY_FILE")
        return emptyMap()
      }
      return lines.associate {
        val values = it.split(",")
        Pair(values[0], values[1].toDoubleOrNull() ?: 0.0)
      }
    }

    private fun getTipUtility(tip: TipAndTrickBean): Double {
      return tips2utility.getOrDefault(tip.fileName, 0.0)
    }
  }

  private inner class TipsUsageListener : FeaturesRegistryListener {
    override fun featureUsed(feature: FeatureDescriptor) {
      TipUIUtil.getTip(feature)?.let { tip ->
        shownTips[tip.fileName]?.let { timestamp ->
          TipsOfTheDayUsagesCollector.triggerTipUsed(tip.fileName, timestamp)
        }
      }
    }
  }
}