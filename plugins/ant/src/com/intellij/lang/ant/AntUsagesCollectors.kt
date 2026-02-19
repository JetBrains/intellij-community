// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.ant

import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector
import com.intellij.lang.ant.config.AntConfiguration
import com.intellij.openapi.project.Project

internal class AntSettingsCollector : ProjectUsagesCollector() {
  override fun getGroup(): EventLogGroup {
    return GROUP
  }

  override fun getMetrics(project: Project): Set<MetricEvent> {
    val buildFiles = AntConfiguration.getInstance(project).buildFileList
    if (buildFiles.isEmpty()) return emptySet()

    val usages = mutableSetOf<MetricEvent>()

    // to have a total users baseline to calculate percentages of settings
    usages.add(HAS_ANT_PROJECTS.metric(true))

    for (each in buildFiles) {
      usages.add(IS_RUN_IN_BACKGROUND.metric(each.isRunInBackground))
      usages.add(IS_COLORED_OUTPUT_MESSAGES.metric(each.isColoredOutputMessages))
      usages.add(IS_VIEW_CLOSED_WHEN_NO_ERRORS.metric(each.isViewClosedWhenNoErrors))
      usages.add(IS_COLLAPSE_FINISHED_TARGETS.metric(each.isCollapseFinishedTargets))
    }

    return usages
  }

  private val GROUP = EventLogGroup("build.ant.state", 2)
  private val HAS_ANT_PROJECTS = GROUP.registerEvent("hasAntProjects", EventFields.Enabled)
  private val IS_RUN_IN_BACKGROUND = GROUP.registerEvent("isRunInBackground", EventFields.Enabled)
  private val IS_COLORED_OUTPUT_MESSAGES = GROUP.registerEvent("isColoredOutputMessages", EventFields.Enabled)
  private val IS_VIEW_CLOSED_WHEN_NO_ERRORS = GROUP.registerEvent("isViewClosedWhenNoErrors", EventFields.Enabled)
  private val IS_COLLAPSE_FINISHED_TARGETS = GROUP.registerEvent("isCollapseFinishedTargets", EventFields.Enabled)
}

internal object AntActionsUsagesCollector : CounterUsagesCollector() {
  override fun getGroup(): EventLogGroup = GROUP

  private val GROUP = EventLogGroup("build.ant.actions", 1)

  @JvmField
  val runSelectedBuildAction = GROUP.registerEvent("RunSelectedBuild")
}
