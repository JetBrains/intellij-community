// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.ant

import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.beans.newBooleanMetric
import com.intellij.internal.statistic.service.fus.collectors.FUCounterUsageLogger
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector
import com.intellij.lang.ant.config.AntConfiguration
import com.intellij.openapi.project.Project

class AntSettingsCollector : ProjectUsagesCollector() {
  override fun getGroupId() = "build.ant.state"

  override fun getMetrics(project: Project): Set<MetricEvent> {
    val buildFiles = AntConfiguration.getInstance(project).buildFileList
    if (buildFiles.isEmpty()) return emptySet()

    val usages = mutableSetOf<MetricEvent>()

    // to have a total users base line to calculate pertentages of settings
    usages.add(newBooleanMetric("hasAntProjects", true))

    for (each in buildFiles) {
      usages.add(newBooleanMetric("isRunInBackground", each.isRunInBackground))
      usages.add(newBooleanMetric("isColoredOutputMessages", each.isColoredOutputMessages))
      usages.add(newBooleanMetric("isViewClosedWhenNoErrors", each.isViewClosedWhenNoErrors))
      usages.add(newBooleanMetric("isCollapseFinishedTargets", each.isCollapseFinishedTargets))
    }

    return usages
  }
}

class AntActionsUsagesCollector {
  enum class ActionID {
    RunSelectedBuild
  }

  companion object {
    @JvmStatic
    fun trigger(project: Project?, actionId: ActionID) {
      if (project == null) return
      FUCounterUsageLogger.getInstance().logEvent(project, "build.ant.actions", actionId.name)
    }
  }
}
