// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus
import kotlin.random.Random

@ApiStatus.Internal
@Service(Service.Level.APP)
@State(name = "ShownTips", storages = [Storage(value = StoragePathMacros.NON_ROAMABLE_FILE)])
internal class TipsUsageManager : PersistentStateComponent<TipsUsageManager.State> {
  companion object {
    @JvmStatic
    fun getInstance(): TipsUsageManager = service()
  }

  private val shownTips = Object2LongOpenHashMap<String>()
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
        }
        else if (!usedTips.contains(tip)) {
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
    shownTips.put(tip.fileName!!, System.currentTimeMillis())
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

  @Serializable
  data class State(val shownTips: Map<String, Long> = emptyMap())

  private class TipsUsageListener : FeaturesRegistryListener {
    override fun featureUsed(feature: FeatureDescriptor) {
      val tip = TipUIUtil.getTip(feature) ?: return
      val timestamp = getInstance().shownTips.getLong(tip.fileName)
      if (timestamp != 0L) {
        TipsOfTheDayUsagesCollector.triggerTipUsed(tip.fileName, System.currentTimeMillis() - timestamp)
      }
    }
  }
}

private class TipsUtilityHolder(private val tipsToUtility: Object2DoubleOpenHashMap<String>) {
  companion object {
    private const val TIPS_UTILITY_FILE = "tips_utility.csv"

    fun readFromResourcesAndCreate(): TipsUtilityHolder = TipsUtilityHolder(readTipsUtility())

    private fun readTipsUtility(): Object2DoubleOpenHashMap<String> {
      val classLoader = TipsUtilityHolder::class.java.classLoader
      val lines = ResourceUtil.getResourceAsBytes(TIPS_UTILITY_FILE, classLoader)?.decodeToString()
      if (lines == null) {
        logger<TipsUtilityHolder>().error("Can't read resource file with tips utilities: $TIPS_UTILITY_FILE")
        return Object2DoubleOpenHashMap()
      }

      val result = Object2DoubleOpenHashMap<String>()
      result.defaultReturnValue(-1.0)
      for (line in lines.lineSequence().filter(String::isNotBlank)) {
        val values = line.split(',')
        result.put(values[0], values[1].toDoubleOrNull() ?: 0.0)
      }
      result.trim()
      return result
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
    var totalUtility = sortedTips.sumOf { it.second }
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

  private fun getTipUtility(tip: TipAndTrickBean) = tipsToUtility.getDouble(tip.fileName)
}