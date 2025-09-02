// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.welcomeScreen.statistics

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventId
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object WslPromoPanelUsageCollector: CounterUsagesCollector()  {

  override fun getGroup(): EventLogGroup {

    return GROUP
  }

  private val GROUP = EventLogGroup("recent.projects.panel", 1)

  private val GO_TO_PROJECTS: EventId = GROUP.registerEvent(
    "go.to.projects.button.clicked",
  )

  private val LEARN_MORE: EventId = GROUP.registerEvent(
    "learn.more.button.clicked",
  )

  fun logGoToProjectsClick() {
    GO_TO_PROJECTS.log()
  }
  fun logLearnMoreClick() {
    LEARN_MORE.log()
  }
}