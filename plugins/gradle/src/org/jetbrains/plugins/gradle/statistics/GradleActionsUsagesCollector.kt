// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.statistics

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventId
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.project.Project

internal object GradleActionsUsagesCollector : CounterUsagesCollector() {
  override fun getGroup(): EventLogGroup = GROUP

  private val GROUP = EventLogGroup("build.gradle.actions", 4)

  @JvmField
  val REFRESH_DAEMONS = GROUP.registerEvent("refreshDaemons")
  @JvmField
  val GRACEFUL_STOP_ALL_DAEMONS = GROUP.registerEvent("gracefulStopAllDaemons")
  @JvmField
  val STOP_ALL_DAEMONS = GROUP.registerEvent("stopAllDaemons")
  @JvmField
  val STOP_SELECTED_DAEMONS = GROUP.registerEvent("stopSelectedDaemons")
  @JvmField
  val PASTE_MAVEN_DEPENDENCY = GROUP.registerEvent("PasteMvnDependency")
  @JvmField
  val SHOW_GRADLE_DAEMONS_ACTION = GROUP.registerEvent("showGradleDaemonsAction")
  @JvmField
  val TOGGLE_PARALLEL_FETCH = GROUP.registerEvent("toggleParallelFetch",
                                                  EventFields.Boolean("new_value", "newly set value"),
                                                  "Parallel model fetch flag was switched by a user to the new value. " +
                                                  "Only fires when the old value differs from the new boolean value")

  @JvmStatic
  fun trigger(project: Project?, action: EventId) {
    if (project == null) return
    action.log(project)
  }
}
