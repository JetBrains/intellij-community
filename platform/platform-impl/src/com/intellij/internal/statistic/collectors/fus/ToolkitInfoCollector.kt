// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.collectors.fus

import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class ToolkitInfoCollector : ApplicationUsagesCollector() {

  private val toolkitNames = listOf("WLToolkit", "XToolkit", "other", "not_set")
  private val GROUP = EventLogGroup("toolkit.info", 1)
  private val selectedLanguage = GROUP.registerEvent("awt.toolkit.name", EventFields.String("value", toolkitNames))

  override fun getMetrics(): Set<MetricEvent> {
    val result = mutableSetOf<MetricEvent>()
    val property = System.getProperty("awt.toolkit.name")
    val value = if (property == null) "not_set" else if (toolkitNames.contains(property)) property else "other"
    result.add(selectedLanguage.metric(value))
   return result
  }

  override fun getGroup(): EventLogGroup {
    return GROUP
  }
}