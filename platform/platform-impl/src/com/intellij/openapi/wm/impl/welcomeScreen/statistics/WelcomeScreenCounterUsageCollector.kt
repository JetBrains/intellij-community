// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.welcomeScreen.statistics

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector

internal object WelcomeScreenCounterUsageCollector : CounterUsagesCollector() {
  private val GROUP = EventLogGroup("welcome.screen", 3)

  private val WELCOME_SCREEN_SHOWN = GROUP.registerEvent(
    "projects.tab.created",
    EventFields.BoundedInt("recent_paths_count", intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 20, 30, 50)),
    EventFields.BoundedInt("provider_recent_projects_count", intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 20, 30, 50))
  )

  fun reportWelcomeScreenShowed(recentPathsCount: Int, providerRecentProjectsCount: Int) =
    WELCOME_SCREEN_SHOWN.log(recentPathsCount, providerRecentProjectsCount)

  override fun getGroup(): EventLogGroup = GROUP
}