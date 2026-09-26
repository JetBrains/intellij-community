// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.eclipse.detect

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector

object EclipseProjectDetectorUsagesCollector : CounterUsagesCollector() {

  override fun getGroup() = GROUP

  private val GROUP = EventLogGroup("eclipse.projects.detector", 2)

  private val projectsDetected = GROUP.registerEvent("detected", EventFields.Int("projectsCount"))
  private val projectOpened = GROUP.registerEvent("opened", EventFields.Boolean("fromEmptyState"))

  @JvmStatic
  fun logProjectsDetected(projectsCount: Int) {
    projectsDetected.log(projectsCount)
  }

  @JvmStatic
  fun logProjectOpened(fromEmptyState: Boolean) {
    projectOpened.log(fromEmptyState)
  }
}