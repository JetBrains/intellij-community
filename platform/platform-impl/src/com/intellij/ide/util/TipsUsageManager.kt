// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util

import com.intellij.featureStatistics.FeatureDescriptor
import com.intellij.featureStatistics.FeaturesRegistryListener
import com.intellij.featureStatistics.ProductivityFeaturesRegistry
import com.intellij.ide.TipsOfTheDayUsagesCollector
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.intellij.openapi.diagnostic.logger
import com.intellij.util.ResourceUtil
import com.intellij.util.text.DateFormatUtil
import org.jetbrains.annotations.ApiStatus
import java.awt.EventQueue
import kotlin.random.Random

@ApiStatus.Internal
@State(name = "ShownTips", storages = [Storage(value = "shownTips.xml", roamingType = RoamingType.DISABLED)])
internal class TipsUsageManager : PersistentStateComponent<TipsUsageManager.State> {
  companion object {
    @JvmStatic
    fun getInstance(): TipsUsageManager = service()
  }

  private val shownTips: MutableMap<String, Long> = mutableMapOf()
  private val utilityHolder = TipsUtilityHolder.readFromResourcesAndCreate()

  init {
    ApplicationManager.getApplication().messageBus.connect().subscribe(FeaturesRegistryListener.TOPIC, TipsUsageListener())
  }

  override fun getState(): State = State(shownTips)

  override fun loadState(state: State) {
    shownTips.putAll(state.shownTips)
  }

  fun sortTips(tips: List<TipAndTrickBean>, experimentType: TipsUtilityExperiment): RecommendationDescription {
    val usedTips = mutableSetOf<TipAndTrickBean>()
    val unusedTips = mutableSetOf<TipAndTrickBean>()
    ProductivityFeaturesRegistry.getInstance()?.let { featuresRegistry ->
      for (featureId in featuresRegistry.featureIds) {
        val feature = featuresRegistry.getFeatureDescriptor(featureId)
        val tip = TipUIUtil.getTip(feature) ?: continue
        if (!tips.contains(tip)) continue
        if (System.currentTimeMillis() - feature.lastTimeUsed < DateFormatUtil.MONTH) {
          usedTips.add(tip)
          unusedTips.remove(tip) // some tips correspond to multiple features
        } else if (!usedTips.contains(tip)) {
          unusedTips.add(tip)
        }
      }
    }
    val otherTips = tips.toSet() - (usedTips + unusedTips)
    val resultTips = when (experimentType) {
      TipsUtilityExperiment.BY_TIP_UTILITY -> {
        val sortedByUtility = utilityHolder.sampleTips(unusedTips + usedTips)
        sortedByUtility + otherTips.shuffled()
      }
      TipsUtilityExperiment.BY_TIP_UTILITY_IGNORE_USED -> {
        val sortedByUtility = utilityHolder.sampleTips(unusedTips)
        sortedByUtility + otherTips.shuffled() + usedTips.shuffled()
      }
      TipsUtilityExperiment.RANDOM_IGNORE_USED -> {
        unusedTips.shuffled() + otherTips.shuffled() + usedTips.shuffled()
      }
    }
    return RecommendationDescription(experimentType.toString(), resultTips, utilityHolder.getMetadataVersion())
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
        resultTips.find { tip -> tip.fileName == it.key }?.let { tip ->
          resultTips.remove(tip)
          resultTips.add(0, tip)
        }
      }
    }
    return resultTips
  }

  fun wereTipsShownToday(): Boolean = System.currentTimeMillis() - (shownTips.maxOfOrNull { it.value } ?: 0) < DateFormatUtil.DAY

  data class State(var shownTips: Map<String, Long> = emptyMap())

  private class TipsUtilityHolder private constructor(private val tips2utility: Map<String, Double>) {
    companion object {
      private val LOG = logger<TipsUtilityHolder>()
      private const val TIPS_UTILITY_FILE = "tips_utility.csv"

      fun create(tipsUtility: Map<String, Double>): TipsUtilityHolder = TipsUtilityHolder(tipsUtility)

      fun readFromResourcesAndCreate(): TipsUtilityHolder = create(readTipsUtility())

      private fun readTipsUtility() : Map<String, Double> {
        assert(!EventQueue.isDispatchThread() || ApplicationManager.getApplication().isUnitTestMode)
        val classLoader = TipsUtilityHolder::class.java.classLoader
        val lines = ResourceUtil.getResourceAsBytes(TIPS_UTILITY_FILE, classLoader)?.decodeToString()?.reader()?.readLines()
        if (lines == null) {
          LOG.error("Can't read resource file with tips utilities: $TIPS_UTILITY_FILE")
          return emptyMap()
        }
        return lines.associate {
          val values = it.split(",")
          Pair(values[0], values[1].toDoubleOrNull() ?: 0.0)
        }
      }
    }

    fun getMetadataVersion(): String = "0.1"

    fun sampleTips(tips: Iterable<TipAndTrickBean>): List<TipAndTrickBean> {
      val (knownTips, unknownTips) = tips.map { Pair(it, getTipUtility(it)) }.partition { it.second >= 0 }
      val result = mutableListOf<TipAndTrickBean>()
      result.sampleByUtility(knownTips)
      result.sampleUnknown(unknownTips)
      return result
    }

    private fun MutableList<TipAndTrickBean>.sampleByUtility(knownTips: List<Pair<TipAndTrickBean, Double>>) {
      val sortedTips = knownTips.sortedByDescending { it.second }.toMutableSet()
      var totalUtility = sortedTips.sumByDouble { it.second }
      for (i in 0 until sortedTips.size) {
        var cumulativeUtility = 0.0
        if (totalUtility <= 0.0) {
          this.addAll(sortedTips.map { it.first }.shuffled())
          break
        }
        val prob = Random.nextDouble(totalUtility)
        for (tip2utility in sortedTips) {
          val tipUtility = tip2utility.second
          cumulativeUtility += tipUtility
          if (prob <= cumulativeUtility) {
            this.add(tip2utility.first)
            totalUtility -= tipUtility
            sortedTips.remove(tip2utility)
            break
          }
        }
      }
    }

    private fun MutableList<TipAndTrickBean>.sampleUnknown(unknownTips: List<Pair<TipAndTrickBean, Double>>) {
      val unknownProb = unknownTips.size.toDouble() / (this.size + unknownTips.size)
      var currentIndex = 0
      for (unknownTip in unknownTips.shuffled()) {
        while (currentIndex < this.size && Random.nextDouble() > unknownProb) {
          currentIndex++
        }
        this.add(currentIndex, unknownTip.first)
        currentIndex++
      }
    }

    private fun getTipUtility(tip: TipAndTrickBean): Double {
      return tips2utility.getOrDefault(tip.fileName, -1.0)
    }
  }

  private inner class TipsUsageListener : FeaturesRegistryListener {
    override fun featureUsed(feature: FeatureDescriptor) {
      TipUIUtil.getTip(feature)?.let { tip ->
        shownTips[tip.fileName]?.let { timestamp ->
          TipsOfTheDayUsagesCollector.triggerTipUsed(tip.fileName, System.currentTimeMillis() - timestamp)
        }
      }
    }
  }
}