// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.ant

import com.intellij.internal.statistic.beans.UsageDescriptor
import com.intellij.internal.statistic.collectors.fus.actions.persistence.ActionsCollectorImpl
import com.intellij.internal.statistic.service.fus.collectors.FUCounterUsageLogger
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector
import com.intellij.internal.statistic.utils.getBooleanUsage
import com.intellij.lang.ant.config.AntConfiguration
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project

class AntSettingsCollector : ProjectUsagesCollector() {
  override fun getGroupId() = "build.ant.state"

  override fun getUsages(project: Project): Set<UsageDescriptor> {
    val buildFiles = AntConfiguration.getInstance(project).buildFileList
    if (buildFiles.isEmpty()) return emptySet()

    val usages = mutableSetOf<UsageDescriptor>()

    // to have a total users base line to calculate pertentages of settings
    usages.add(getBooleanUsage("hasAntProjects", true))

    for (each in buildFiles) {
      usages.add(getBooleanUsage("isRunInBackground", each.isRunInBackground))
      usages.add(getBooleanUsage("isColoredOutputMessages", each.isColoredOutputMessages))
      usages.add(getBooleanUsage("isViewClosedWhenNoErrors", each.isViewClosedWhenNoErrors))
      usages.add(getBooleanUsage("isCollapseFinishedTargets", each.isCollapseFinishedTargets))
    }

    return usages
  }
}

class AntActionsUsagesCollector {
  companion object {
    @JvmStatic
    fun trigger(project: Project?, action: AnAction, event: AnActionEvent?) {
      ActionsCollectorImpl.record("build.ant.actions", project, action, event, null)
    }

    @JvmStatic
    fun trigger(project: Project?, feature: String) {
      if (project == null) return
      FUCounterUsageLogger.getInstance().logEvent(project, "build.ant.actions", feature)
    }
  }
}
