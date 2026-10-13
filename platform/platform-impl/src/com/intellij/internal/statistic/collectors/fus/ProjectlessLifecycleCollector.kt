// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.collectors.fus

import com.intellij.ide.welcomeScreen.WelcomeUtils
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import org.jetbrains.annotations.ApiStatus

/**
 * Reports when the projectless window opens and why it closes.
 */
@ApiStatus.Internal
object ProjectlessLifecycleCollector : CounterUsagesCollector() {
  override fun getGroup(): EventLogGroup = GROUP

  private val GROUP = EventLogGroup("projectless.lifecycle", 1)

  /**
   * Marks a project that closes because another project opens in its frame.
   */
  @JvmField
  val CLOSED_BY_ANOTHER_PROJECT: Key<Boolean> = Key.create("projectless.lifecycle.closed.by.another.project")

  private val REASON = EventFields.Enum<CloseReason>("reason") { it.name.lowercase() }

  private val OPENED = GROUP.registerEvent("opened")
  private val CLOSED = GROUP.registerEvent("closed", REASON)

  @JvmStatic
  fun onProjectOpened(project: Project) {
    if (WelcomeUtils.isWelcomeProject(project)) {
      OPENED.log(project)
    }
  }

  @JvmStatic
  fun onBeforeProjectClosed(project: Project, projectless: EventPair<*>?) {
    if (projectless?.data == true) {
      CLOSED.log(project, getCloseReason(project))
    }
  }

  private fun getCloseReason(project: Project): CloseReason {
    return when {
      ApplicationManager.getApplication().isExitInProgress -> CloseReason.IDE_EXIT
      project.getUserData(CLOSED_BY_ANOTHER_PROJECT) == true -> CloseReason.ANOTHER_PROJECT
      else -> CloseReason.USER
    }
  }

  private enum class CloseReason { USER, ANOTHER_PROJECT, IDE_EXIT }
}
