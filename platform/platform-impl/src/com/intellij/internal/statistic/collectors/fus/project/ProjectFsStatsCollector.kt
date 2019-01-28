// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.collectors.fus.project

import com.intellij.internal.statistic.eventLog.FeatureUsageData
import com.intellij.internal.statistic.eventLog.FeatureUsageGroup
import com.intellij.internal.statistic.eventLog.FeatureUsageLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo

object ProjectFsStatsCollector {
  private val groupId = FeatureUsageGroup("project.fs", 1)

  @JvmStatic
  fun caseSensitivity(project: Project, value: Boolean) {
    FeatureUsageLogger.logState(groupId, "case-sensitivity", FeatureUsageData()
      .addProject(project)
      .addData("cs-project", value)
      .addData("cs-system", SystemInfo.isFileSystemCaseSensitive)
      .addData("cs-implicit", System.getProperty("idea.case.sensitive.fs") == null)
      .addOS()
      .build())
  }

  @JvmStatic
  fun watchedRoots(project: Project, pctNonWatched: Int) {
    FeatureUsageLogger.logState(groupId, "roots-watched", FeatureUsageData()
      .addProject(project)
      .addData("pct-non-watched", pctNonWatched)
      .addData("os-name", SystemInfo.OS_NAME)  // `addOS` groups uncommon OSes under 'Other' moniker
      .build())
  }
}