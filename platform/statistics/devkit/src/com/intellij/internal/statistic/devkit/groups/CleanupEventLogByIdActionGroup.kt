// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.devkit.groups

import com.intellij.internal.statistic.StatisticsBundle
import com.intellij.internal.statistic.devkit.StatisticsDevKitUtil.getLogProvidersInTestMode
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

internal class CleanupEventLogByIdActionGroup : ActionGroup() {

  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    e.presentation.isPopupGroup = getLogProvidersInTestMode().size > 1
  }

  override fun getChildren(e: AnActionEvent?): Array<AnAction> {
    return getLogProvidersInTestMode()
      .map { logger ->
        val recorder = logger.recorderId
        val actionText = StatisticsBundle.message("stats.cleanup.0.event.log", recorder)
        com.intellij.internal.statistic.devkit.actions.CleanupEventLogAction(recorder, actionText)
      }
      .toTypedArray()
  }
}