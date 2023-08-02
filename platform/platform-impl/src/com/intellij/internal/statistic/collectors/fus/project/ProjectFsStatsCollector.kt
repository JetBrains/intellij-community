// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.collectors.fus.project

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.FeatureUsageData
import com.intellij.internal.statistic.service.fus.collectors.FUStateUsagesLogger
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
object ProjectFsStatsCollector {
  private val groupId = EventLogGroup("project.fs", 2)

  fun watchedRoots(project: Project, pctNonWatched: Int) {
    FUStateUsagesLogger.logStateEvent(groupId, "roots-watched", FeatureUsageData(groupId.recorder)
      .addProject(project)
      .addData("pct-non-watched", pctNonWatched))
  }
}
