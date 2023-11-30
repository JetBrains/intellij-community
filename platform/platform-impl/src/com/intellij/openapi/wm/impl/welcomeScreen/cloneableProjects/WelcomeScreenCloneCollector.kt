// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.welcomeScreen.cloneableProjects

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector

internal object WelcomeScreenCloneCollector : CounterUsagesCollector() {
  override fun getGroup(): EventLogGroup {
    return GROUP
  }

  private val GROUP = EventLogGroup("welcome_screen.clone", 1)

  private val CLONE_ADDED_FROM_WELCOME_SCREEN = GROUP.registerEvent("added", EventFields.Int("cloneable_projects"))
  private val CLONE_CANCELED_FROM_WELCOME_SCREEN = GROUP.registerEvent("canceled")
  private val CLONE_SUCCESS_FROM_WELCOME_SCREEN = GROUP.registerEvent("success")
  private val CLONE_FAILED_FROM_WELCOME_SCREEN = GROUP.registerEvent("failed")

  fun cloneAdded(cloneableProjects: Int) {
    CLONE_ADDED_FROM_WELCOME_SCREEN.log(cloneableProjects)
  }

  fun cloneSuccess() {
    CLONE_SUCCESS_FROM_WELCOME_SCREEN.log()
  }

  fun cloneFailed() {
    CLONE_FAILED_FROM_WELCOME_SCREEN.log()
  }

  fun cloneCanceled() {
    CLONE_CANCELED_FROM_WELCOME_SCREEN.log()
  }
}