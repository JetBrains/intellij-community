// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.actions

import com.intellij.icons.AllIcons
import com.intellij.idea.ActionsBundle
import com.intellij.internal.statistic.StatisticsBundle
import com.intellij.internal.statistic.eventLog.validator.SensitiveDataValidator
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAwareAction

class UpdateEventsSchemeAction(val recorder: String)
  : DumbAwareAction(StatisticsBundle.message("stats.update.events.scheme"),
                    ActionsBundle.message("group.UpdateEventsSchemeAction.description"),
                    AllIcons.Actions.Refresh) {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return

    ProgressManager.getInstance().run(object : Task.Backgroundable(project, StatisticsBundle.message("stats.updating.events.scheme"), false) {
      override fun run(indicator: ProgressIndicator) {
        val validator = SensitiveDataValidator.getInstance(recorder)
        validator.update()
        validator.reload()
      }
    })
  }

}