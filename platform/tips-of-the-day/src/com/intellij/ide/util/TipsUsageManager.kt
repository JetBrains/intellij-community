// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util

import com.intellij.featureStatistics.FeatureDescriptor
import com.intellij.featureStatistics.FeaturesRegistryListener
import com.intellij.ide.TipsOfTheDayUsagesCollector
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

@ApiStatus.Internal
@Service(Service.Level.APP)
@State(name = "ShownTips", storages = [Storage(value = StoragePathMacros.NON_ROAMABLE_FILE)])
internal class TipsUsageManager : PersistentStateComponent<TipsUsageManager.State> {
  companion object {
    @JvmStatic
    fun getInstance(): TipsUsageManager = service()
  }

  private val shownTips = Object2LongOpenHashMap<String>()

  init {
    ApplicationManager.getApplication().messageBus.connect().subscribe(FeaturesRegistryListener.TOPIC, TipsUsageListener())
  }

  override fun getState(): State = State(shownTips)

  override fun loadState(state: State) {
    state.shownTips.forEach { (filename, timestamp) ->
      // migration from tip filename to tip id
      shownTips.put(TipAndTrickBean.getTipId(filename), timestamp)
    }
  }

  fun fireTipShown(tip: TipAndTrickBean) {
    shownTips.put(tip.id, System.currentTimeMillis())
  }

  fun getLastTimeShown(tipId: String): Long {
    return shownTips.getLong(tipId)
  }

  fun makeLastShownTipFirst(tips: List<TipAndTrickBean>): List<TipAndTrickBean> {
    val resultTips = tips.toMutableList()
    shownTips.maxByOrNull { it.value }?.let {
      resultTips.find { tip -> tip.id == it.key }?.let { tip ->
        resultTips.remove(tip)
        resultTips.add(0, tip)
      }
    }
    return resultTips
  }

  fun wereTipsShownToday(): Boolean {
    val lastShownTimeMillis = shownTips.maxOfOrNull { it.value } ?: 0
    val currentZoneId = ZoneId.systemDefault()
    val curDayStartTime = LocalDate.now(currentZoneId).atStartOfDay()
    val lastShownTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(lastShownTimeMillis), currentZoneId)
    return lastShownTime.isAfter(curDayStartTime)
  }

  @Serializable
  data class State(val shownTips: Map<String, Long> = emptyMap())

  private class TipsUsageListener : FeaturesRegistryListener {
    override fun featureUsed(feature: FeatureDescriptor) {
      val tip = TipUtils.getTip(feature) ?: return
      val timestamp = getInstance().getLastTimeShown(tip.id)
      if (timestamp != 0L) {
        TipsOfTheDayUsagesCollector.triggerTipUsed(tip.id, System.currentTimeMillis() - timestamp)
      }
    }
  }
}
