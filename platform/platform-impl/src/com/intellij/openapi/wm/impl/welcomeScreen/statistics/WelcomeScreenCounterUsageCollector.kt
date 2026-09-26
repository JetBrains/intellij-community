// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.welcomeScreen.statistics

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector

internal object WelcomeScreenCounterUsageCollector : CounterUsagesCollector() {
  private val GROUP = EventLogGroup("welcome.screen", 4)

  private val WELCOME_SCREEN_SHOWN = GROUP.registerEvent(
    "projects.tab.created",
    EventFields.BoundedInt("recent_paths_count", intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 20, 30, 50)),
    EventFields.BoundedInt("provider_recent_projects_count", intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 20, 30, 50))
  )
  private val LEARN_ENABLE_ACCESS_BUTTON_CLICKED = GROUP.registerEvent("learn.enable.access.button.clicked")
  private val LEARN_GET_STARTED_BUTTON_CLICKED = GROUP.registerEvent("learn.get.started.button.clicked")

  fun reportWelcomeScreenShowed(recentPathsCount: Int, providerRecentProjectsCount: Int) =
    WELCOME_SCREEN_SHOWN.log(recentPathsCount, providerRecentProjectsCount)

  // Events for Learn tab
  fun reportLearnEnableAccessButtonClicked() = LEARN_ENABLE_ACCESS_BUTTON_CLICKED.log()
  fun reportLearnGetStartedButtonClicked() = LEARN_GET_STARTED_BUTTON_CLICKED.log()

  override fun getGroup(): EventLogGroup = GROUP
}