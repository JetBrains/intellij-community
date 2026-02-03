// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea

import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.openapi.actionSystem.AnActionEvent
import git4idea.i18n.GitBundle
import git4idea.stash.GitChangesSaver

internal class GitRestoreSavedChangesNotificationAction(private val saver: GitChangesSaver) : NotificationAction(
  saver.saveMethod.selectBundleMessage(
    GitBundle.message("rebase.notification.action.view.stash.text"),
    GitBundle.message("rebase.notification.action.view.shelf.text")
  )
) {
  override fun actionPerformed(e: AnActionEvent, notification: Notification) {
    saver.showSavedChanges()
  }
}