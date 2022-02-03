// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.devkit.actions

import com.intellij.icons.AllIcons
import com.intellij.idea.ActionsBundle
import com.intellij.internal.statistic.StatisticsBundle
import com.intellij.internal.statistic.eventLog.validator.IntellijSensitiveDataValidator
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAwareAction

class UpdateEventsSchemeAction(val recorder: String)
  : DumbAwareAction(StatisticsBundle.message("stats.update.0.events.scheme", recorder),
                    ActionsBundle.message("group.UpdateEventsSchemeAction.description"),
                    AllIcons.Actions.Refresh) {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project

    ProgressManager.getInstance().run(object : Task.Backgroundable(project, StatisticsBundle.message("stats.updating.events.scheme"), false) {
      override fun run(indicator: ProgressIndicator) {
        val validator = IntellijSensitiveDataValidator.getInstance(recorder)
        validator.update()
        validator.reload()
      }
    })
  }

}