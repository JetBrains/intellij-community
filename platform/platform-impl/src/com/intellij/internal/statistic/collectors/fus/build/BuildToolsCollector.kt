// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.collectors.fus.build

import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventId1
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector
import com.intellij.openapi.progress.checkCanceled
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.EnvironmentScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class BuildToolsCollector : ApplicationUsagesCollector() {
  private val GROUP: EventLogGroup = EventLogGroup("build.cli", 1)

  override fun getGroup(): EventLogGroup = GROUP

  private val BUILD_EXECUTABLES: List<String> = listOf(
    "ant",
    "mvn",
    "gradle",
    "bazel",
    "sbt",
    "pants",
    "buck",
    "amper"
  )

  private val TOOL_INSTALLED: EventId1<String> = GROUP.registerEvent(
    "tool.installed",
    EventFields.String("tool", BUILD_EXECUTABLES)
  )

  override suspend fun getMetricsAsync(): Set<MetricEvent> {
    return withContext(Dispatchers.IO) {
      val pathNames = EnvironmentScanner.getPathNames()
      BUILD_EXECUTABLES
        .filter {
          checkCanceled()

          EnvironmentScanner.hasToolInLocalPath(pathNames, it)
        }
        .map { TOOL_INSTALLED.metric(it) }
        .toSet()
    }
  }
}