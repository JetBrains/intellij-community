// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl

import com.intellij.ide.IdeBundle
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.util.TimeoutUtil

fun restartOrNotify(project: Project,
                    restartAutomatically: Boolean,
                    @NlsContexts.DialogTitle progressTitle: String = IdeBundle.message("action.UpdateIde.progress.title"),
                    restart: () -> Unit) {
  if (restartAutomatically) {
    ApplicationManager.getApplication().invokeLater {
      scheduleRestart(project, progressTitle) { restart() }
    }
  }
  else {
    showRestartNotification(project, restart)
  }
}

private fun showRestartNotification(project: Project, restart: () -> Unit) {
  @Suppress("DialogTitleCapitalization")
  Notification("IDE and Plugin Updates", IdeBundle.message("action.UpdateIde.task.success.title"),
               IdeBundle.message("action.UpdateIde.task.success.content"), NotificationType.INFORMATION)
    .addAction(NotificationAction.createSimpleExpiring(
      IdeBundle.message("action.UpdateIde.task.success.restart")) { restart() })
    .notify(project)
}

private fun scheduleRestart(project: Project,
                            @NlsContexts.DialogTitle progressTitle: String,
                            restart: () -> Unit) {
  object : Task.Modal(project, progressTitle, true) {
    override fun run(indicator: ProgressIndicator) {
      indicator.isIndeterminate = false
      var progress = 0
      for (i in 10 downTo 1) {
        indicator.text = IdeBundle.message("action.UpdateIde.progress.text.ide.will.restart", i)
        repeat(10) {
          indicator.fraction = 0.01 * progress++
          indicator.checkCanceled()
          TimeoutUtil.sleep(100)
        }
      }
      restart()
    }

    override fun onCancel() {
      showRestartNotification(project, restart)
    }
  }.setCancelText(IdeBundle.message("action.UpdateIde.button.postpone")).queue()
}
