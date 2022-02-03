// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.devkit.actions

import com.intellij.internal.statistic.eventLog.StatisticsEventLogProviderUtil
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task

internal class CleanupEventLogAction(val recorderId: String, val actionText: String) : AnAction(actionText) {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project

    ProgressManager.getInstance().run(object : Task.Backgroundable(project, actionText, false) {
      override fun run(indicator: ProgressIndicator) {
        val provider = StatisticsEventLogProviderUtil.getEventLogProvider(recorderId)
        provider.logger.cleanup()
      }
    })
  }
}