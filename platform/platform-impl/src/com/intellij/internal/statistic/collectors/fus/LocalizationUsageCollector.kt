// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.collectors.fus

import com.intellij.ide.Region
import com.intellij.ide.RegionSettings
import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector
import com.intellij.l10n.LocalizationUtil
import java.util.*

class LocalizationUsageCollector : ApplicationUsagesCollector() {
  private val GROUP = EventLogGroup("localization.info", 1)
  private val selectedLanguage = GROUP.registerEvent("selected.language", EventFields.Enum("value", IdeLanguage::class.java))
  private val selectedRegion = GROUP.registerEvent("selected.region", EventFields.Enum("value", Region::class.java))

  @Suppress("EnumEntryName")
  private enum class IdeLanguage {
    chinese, japanese, korean, default, other
  }

  override fun getMetrics(): Set<MetricEvent> {
    val result = mutableSetOf<MetricEvent>()
    val language = when (LocalizationUtil.getLocale()) {
      Locale.SIMPLIFIED_CHINESE -> IdeLanguage.chinese
      Locale.JAPANESE -> IdeLanguage.japanese
      Locale.KOREAN -> IdeLanguage.korean
      Locale.ENGLISH -> IdeLanguage.default
      else -> IdeLanguage.other
    }
    result.add(selectedLanguage.metric(language))
    val region = RegionSettings.getRegion()
    result.add(selectedRegion.metric(region))
    return result
  }

  override fun getGroup(): EventLogGroup {
    return GROUP
  }
}