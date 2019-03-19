// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.collectors.fus.project

import com.intellij.internal.statistic.eventLog.FeatureUsageData
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.service.fus.collectors.FUStateUsagesLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo

object ProjectFsStatsCollector {
  private val groupId = EventLogGroup("project.fs", 1)

  @JvmStatic
  fun caseSensitivity(project: Project, value: Boolean) {
    FUStateUsagesLogger.logStateEvent(groupId, "case-sensitivity", FeatureUsageData()
      .addProject(project)
      .addData("cs-project", value)
      .addData("cs-system", SystemInfo.isFileSystemCaseSensitive)
      .addData("cs-implicit", System.getProperty("idea.case.sensitive.fs") == null)
      .addOS())
  }

  @JvmStatic
  fun watchedRoots(project: Project, pctNonWatched: Int) {
    FUStateUsagesLogger.logStateEvent(groupId, "roots-watched", FeatureUsageData()
      .addProject(project)
      .addData("pct-non-watched", pctNonWatched))
  }
}