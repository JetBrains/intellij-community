// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.collectors.fus

import com.intellij.ide.Region
import com.intellij.ide.RegionSettings
import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.StringEventField
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector
import com.intellij.l10n.LocalizationUtil

class LocalizationUsageCollector : ApplicationUsagesCollector() {
  private val GROUP = EventLogGroup("localization.info", 2)
  private val selectedLanguage = GROUP.registerEvent("selected.language", StringEventField.ValidatedByRegexp("value", ".*{2,5}"))
  private val selectedRegion = GROUP.registerEvent("selected.region", EventFields.Enum<Region>("value"))

  override fun getMetrics(): Set<MetricEvent> {
    val result = mutableSetOf<MetricEvent>()
    result.add(selectedLanguage.metric(LocalizationUtil.getLocale().toLanguageTag()))
    val region = RegionSettings.getRegion()
    result.add(selectedRegion.metric(region))
    return result
  }

  override fun getGroup(): EventLogGroup {
    return GROUP
  }
}