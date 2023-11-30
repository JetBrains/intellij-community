// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.diagnostic.startUpPerformanceReporter

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager

private val GROUP = EventLogGroup("startup", 5)

private val DURATION = EventFields.Int("duration")
private val PROJECTS_COUNT = EventFields.Int("projects_count")

private val SPLASH_SHOWN = GROUP.registerEvent("splashShown", DURATION)
private val SPLASH_HIDDEN = GROUP.registerEvent("splashHidden", DURATION)
private val PROJECT_FRAME_VISIBLE = GROUP.registerEvent("projectFrameVisible", DURATION)
private val TOTAL_DURATION = GROUP.registerVarargEvent("totalDuration", DURATION, PROJECTS_COUNT)

private val BOOTSTRAP = GROUP.registerEvent("bootstrap", DURATION)
private val SPLASH = GROUP.registerEvent("splash", DURATION)
private val APP_INIT = GROUP.registerEvent("appInit", DURATION)

private class StartupPerformanceCollector : CounterUsagesCollector() {
  override fun getGroup(): EventLogGroup = GROUP
}

internal fun logStartupPerformanceEvent(project: Project?, eventId: String, duration: Int) {
  when (eventId) {
    "bootstrap" -> BOOTSTRAP.log(duration)
    "splash" -> SPLASH.log(duration)
    "app initialization" -> APP_INIT.log(duration)
    "event:splash shown" -> SPLASH_SHOWN.log(duration)
    "event:splash hidden" -> SPLASH_HIDDEN.log(duration)
    "projectFrameVisible" -> PROJECT_FRAME_VISIBLE.log(duration)
    "totalDuration" -> {
      val openedProjects = ProjectManager.getInstance().openProjects.size
      val projectToReport: Project? = if (openedProjects == 1) project else null
      // total duration here it is Activities.PROJECT_DUMB_POST_START_UP_ACTIVITIES
      TOTAL_DURATION.log(projectToReport, DURATION.with(duration), PROJECTS_COUNT.with(openedProjects))
    }
    else -> logger<StartupPerformanceCollector>().error("Trying to log unknown startup performance metric ('$eventId'). To record it, register a corresponding event and increment group version.")
  }
}
