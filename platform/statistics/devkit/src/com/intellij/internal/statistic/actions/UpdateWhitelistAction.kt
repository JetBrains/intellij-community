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

class UpdateWhitelistAction(val recorder: String)
  : DumbAwareAction(StatisticsBundle.message("stats.update.whitelist"),
                    ActionsBundle.message("group.UpdateWhitelistAction.description"),
                    AllIcons.Actions.Refresh) {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return

    ProgressManager.getInstance().run(object : Task.Backgroundable(project, StatisticsBundle.message("stats.updating.whitelist"), false) {
      override fun run(indicator: ProgressIndicator) {
        SensitiveDataValidator.getInstance(recorder).update()
      }
    })
  }

}