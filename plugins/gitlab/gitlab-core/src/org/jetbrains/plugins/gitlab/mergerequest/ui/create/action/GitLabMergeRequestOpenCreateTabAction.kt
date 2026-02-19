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
import com.intellij.openapi.project.Project
import com.intellij.vcs.gitlab.icons.GitlabIcons
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccount
import org.jetbrains.plugins.gitlab.mergerequest.ui.GitLabProjectViewModel
import org.jetbrains.plugins.gitlab.util.GitLabBundle
import org.jetbrains.plugins.gitlab.util.GitLabProjectMapping
import org.jetbrains.plugins.gitlab.util.GitLabStatistics

internal class GitLabMergeRequestOpenCreateTabAction : DumbAwareAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun update(e: AnActionEvent) {
    val project = e.project ?: run {
      e.presentation.isEnabledAndVisible = false
      return
    }
    val vm = project.service<GitLabProjectViewModel>()
    val vmAvailable = vm.isAvailable.value
    val vmProjectConnected = vm.connectedProjectVm.value != null

    if (e.place == ActionPlaces.TOOLWINDOW_TITLE) {
      e.presentation.isEnabledAndVisible = vmProjectConnected
      e.presentation.icon = AllIcons.General.Add
    }
    else {
      e.presentation.isEnabledAndVisible = vmAvailable
      e.presentation.icon = GitlabIcons.GitLabLogo
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    val place = if (e.place == ActionPlaces.TOOLWINDOW_TITLE) GitLabStatistics.ToolWindowOpenTabActionPlace.TOOLWINDOW
    else GitLabStatistics.ToolWindowOpenTabActionPlace.ACTION
    openNewMergeRequestDetails(e, place)
  }
}

internal class GitLabOpenMergeRequestExistingTabNotificationAction(
  private val project: Project,
  private val projectMapping: GitLabProjectMapping,
  private val account: GitLabAccount,
  private val existingMrOrNull: String,
) : NotificationAction(GitLabBundle.message("merge.request.notification.open.action.text")) {
  override fun actionPerformed(e: AnActionEvent, notification: Notification) {
    val twVm = project.service<GitLabProjectViewModel>()
    val selectorVm = twVm.selectorVm.value ?: error("Tool window has not been initialized")
    selectorVm.selectRepoAndAccount(projectMapping, account)
    selectorVm.submitSelection()
    openExistingTab(e, existingMrOrNull)
  }
}

private fun openExistingTab(event: AnActionEvent, mrId: String) {
  event.project!!.service<GitLabProjectViewModel>().activateAndAwaitProject {
    openMergeRequestDetails(mrId, GitLabStatistics.ToolWindowOpenTabActionPlace.NOTIFICATION)
  }
}

// NOTE: no need to register in plugin.xml (or any xml-file replacing plugin.xml)
internal class GitLabMergeRequestOpenCreateTabNotificationAction(
  private val project: Project,
  private val projectMapping: GitLabProjectMapping,
  private val account: GitLabAccount,
) : NotificationAction(GitLabBundle.message("merge.request.notification.create.action.text")) {
  override fun actionPerformed(e: AnActionEvent, notification: Notification) {
    val vm = project.service<GitLabProjectViewModel>()
    val selectorVm = vm.selectorVm.value ?: error("Tool window has not been initialized")
    selectorVm.selectRepoAndAccount(projectMapping, account)
    selectorVm.submitSelection()

    openNewMergeRequestDetails(e, GitLabStatistics.ToolWindowOpenTabActionPlace.NOTIFICATION)
  }
}

private fun openNewMergeRequestDetails(event: AnActionEvent, place: GitLabStatistics.ToolWindowOpenTabActionPlace) {
  event.project!!.service<GitLabProjectViewModel>().activateAndAwaitProject {
    openMergeRequestDetails(null, place, false)
  }
}