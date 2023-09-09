// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.welcomeScreen.statistics

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventField
import com.intellij.internal.statistic.eventLog.events.EventFields.Int
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.internal.statistic.utils.StatisticsUtil

internal object WelcomeScreenCounterUsageCollector : CounterUsagesCollector() {
  private val GROUP = EventLogGroup("welcome.screen", 1)
  private val RECENT_PATHS_COUNT: EventField<Int> = Int("recent_paths_count")
  private val WELCOME_SCREEN_SHOWN = GROUP.registerEvent("projects.tab.created", RECENT_PATHS_COUNT)

  private val BOUNDS = intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 20, 30, 50)

  fun reportWelcomeScreenShowed(recentPathsCount: Int) {
    WELCOME_SCREEN_SHOWN.log(StatisticsUtil.roundToUpperBound(recentPathsCount, BOUNDS))
  }

  override fun getGroup(): EventLogGroup {
    return GROUP
  }
}