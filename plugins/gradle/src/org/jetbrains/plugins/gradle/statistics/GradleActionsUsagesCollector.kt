// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.statistics

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventId
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.project.Project

class GradleActionsUsagesCollector : CounterUsagesCollector() {
  override fun getGroup(): EventLogGroup = GROUP

  companion object {
    private val GROUP = EventLogGroup("build.gradle.actions", 2)

    @JvmField
    val REFRESH_DAEMONS = GROUP.registerEvent("refreshDaemons")

    @JvmField
    val STOP_ALL_DAEMONS = GROUP.registerEvent("stopAllDaemons")

    @JvmField
    val STOP_SELECTED_DAEMONS = GROUP.registerEvent("stopSelectedDaemons")

    @JvmField
    val PASTE_MAVEN_DEPENDENCY = GROUP.registerEvent("PasteMvnDependency")

    @JvmField
    val SHOW_GRADLE_DAEMONS_ACTION = GROUP.registerEvent("showGradleDaemonsAction")

    @JvmStatic
    fun trigger(project: Project?, action: EventId) {
      if (project == null) return
      action.log(project)
    }
  }
}
