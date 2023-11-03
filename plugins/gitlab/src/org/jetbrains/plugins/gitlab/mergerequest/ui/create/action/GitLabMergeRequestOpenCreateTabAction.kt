// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.create.action

import com.intellij.icons.AllIcons
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareAction
import org.jetbrains.plugins.gitlab.GitlabIcons
import org.jetbrains.plugins.gitlab.mergerequest.ui.toolwindow.model.GitLabToolWindowViewModel
import org.jetbrains.plugins.gitlab.util.GitLabBundle

internal class GitLabMergeRequestOpenCreateTabAction : DumbAwareAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun update(e: AnActionEvent) {
    val project = e.project ?: run {
      e.presentation.isEnabledAndVisible = false
      return
    }
    val twVm = project.service<GitLabToolWindowViewModel>()
    val twAvailable = twVm.isAvailable.value
    val twInitialized = twVm.projectVm.value != null

    if (e.place == ActionPlaces.TOOLWINDOW_TITLE) {
      e.presentation.isEnabledAndVisible = twInitialized
      e.presentation.icon = AllIcons.General.Add
    }
    else {
      e.presentation.isEnabledAndVisible = twAvailable
      e.presentation.icon = GitlabIcons.GitLabLogo
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    openCreationTab(e)
  }
}

// NOTE: no need to register in plugin.xml
internal class GitLabMergeRequestOpenCreateTabNotificationAction : NotificationAction(
  GitLabBundle.message("merge.request.create.notification.action.text")
) {
  override fun actionPerformed(e: AnActionEvent, notification: Notification) {
    openCreationTab(e)
  }
}

private fun openCreationTab(event: AnActionEvent) {
  event.project!!.service<GitLabToolWindowViewModel>().activateAndAwaitProject {
    showCreationTab()
  }
}