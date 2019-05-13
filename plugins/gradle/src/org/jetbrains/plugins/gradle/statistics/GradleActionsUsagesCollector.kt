// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.statistics

import com.intellij.internal.statistic.eventLog.FeatureUsageGroup
import com.intellij.internal.statistic.eventLog.FeatureUsageLogger
import com.intellij.internal.statistic.service.fus.collectors.FUSUsageContext
import com.intellij.internal.statistic.service.fus.collectors.UsageDescriptorKeyValidator
import com.intellij.internal.statistic.utils.createData
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project

private val GROUP = FeatureUsageGroup("statistics.build.gradle.actions",1)

class GradleActionsUsagesCollector {
  companion object {
    @JvmStatic
    fun trigger(project: Project?, action: AnAction, event: AnActionEvent?) {
      if (project == null) return

      // preserve context data ordering
      val context = FUSUsageContext.create(
        event?.place ?: "undefined.place",
        "fromContextMenu.${event?.isFromContextMenu?.toString() ?: "false"}"
      )

      val actionClassName = UsageDescriptorKeyValidator.ensureProperKey(action.javaClass.simpleName)

      FeatureUsageLogger.log(GROUP, actionClassName, createData(project, context))
    }

    @JvmStatic
    fun trigger(project: Project?, feature: String) {
      if (project == null) return
      FeatureUsageLogger.log(GROUP, feature, createData(project, null))
    }
  }
}
