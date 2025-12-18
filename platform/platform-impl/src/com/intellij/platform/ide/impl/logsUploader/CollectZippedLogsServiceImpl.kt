// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.impl.logsUploader

import com.intellij.CommonBundle
import com.intellij.ide.IdeBundle
import com.intellij.ide.actions.COLLECT_LOGS_NOTIFICATION_GROUP
import com.intellij.ide.actions.RevealFileAction
import com.intellij.ide.logsUploader.LogPacker
import com.intellij.ide.util.PropertiesComponent
import com.intellij.idea.ActionsBundle
import com.intellij.logCollector.CollectZippedLogsService
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DoNotAskOption
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.ui.Messages
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.util.ui.IoErrorText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.io.IOException

@ApiStatus.Internal
class CollectZippedLogsServiceImpl(val coroutineScope: CoroutineScope) : CollectZippedLogsService {

  private val CONFIRMATION_DIALOG = "zipped.logs.action.show.confirmation.dialog"

  override fun collectZippedLogs(project: Project?) {
    val doNotShowDialog = PropertiesComponent.getInstance().getBoolean(CONFIRMATION_DIALOG)
    if (!doNotShowDialog) {
      val title = IdeBundle.message("collect.logs.sensitive.title")
      val message = IdeBundle.message("collect.logs.sensitive.text")
      val confirmed = MessageDialogBuilder.Companion.okCancel(title, message)
        .yesText(ActionsBundle.message("action.RevealIn.name.other", RevealFileAction.getFileManagerName()))
        .noText(CommonBundle.getCancelButtonText())
        .icon(Messages.getWarningIcon())
        .doNotAsk(object : DoNotAskOption.Adapter() {
          override fun rememberChoice(selected: Boolean, exitCode: Int) {
            PropertiesComponent.getInstance().setValue(CONFIRMATION_DIALOG, selected)
          }
        })
        .ask(project)
      if (!confirmed) {
        return
      }
    }

    coroutineScope.launch {
      collectLogs(project)
    }
  }

  private suspend fun collectLogs(project: Project? = null) {
    try {
      val logs =
        if (project == null) {
          withContext(Dispatchers.EDT) {
            runWithModalProgressBlocking(
              owner = ModalTaskOwner.guess(),
              title = IdeBundle.message("collect.logs.progress.title"),
              action = { -> LogPacker.packLogs(project) },
            )
          }
        }
        else {
          withBackgroundProgress(
            project = project,
            title = IdeBundle.message("collect.logs.progress.title"),
            action = { -> LogPacker.packLogs(project) },
          )
        }

      if (RevealFileAction.isSupported()) {
        RevealFileAction.openFile(logs)
      }
      else {
        val notification = Notification(
          COLLECT_LOGS_NOTIFICATION_GROUP,
          IdeBundle.message("collect.logs.notification.success", logs),
          NotificationType.INFORMATION
        )
        notification.notify(project)
      }
    }
    catch (e: IOException) {
      thisLogger().warn(e)
      val notification = Notification(
        COLLECT_LOGS_NOTIFICATION_GROUP,
        IdeBundle.message("collect.logs.notification.error", IoErrorText.message(e)),
        NotificationType.ERROR
      )
      notification.notify(project)
    }
  }
}