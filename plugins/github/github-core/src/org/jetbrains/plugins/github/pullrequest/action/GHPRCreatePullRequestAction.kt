// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.action

import com.intellij.icons.AllIcons
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.ui.toolwindow.model.GHPRToolWindowViewModel
import org.jetbrains.plugins.github.util.GHGitRepositoryMapping

internal class GHPRCreatePullRequestAction : DumbAwareAction() {
  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.EDT
  }

  override fun update(e: AnActionEvent) {
    with(e) {
      val vm = project?.service<GHPRToolWindowViewModel>()
      val twAvailable = project != null && vm != null && vm.isAvailable.value
      val twInitialized = project != null && vm != null && vm.projectVm.value != null

      if (place == ActionPlaces.TOOLWINDOW_TITLE) {
        presentation.isEnabledAndVisible = twInitialized
        presentation.icon = AllIcons.General.Add
      }
      else {
        presentation.isEnabledAndVisible = twAvailable
        presentation.icon = AllIcons.Vcs.Vendors.Github
      }
    }
  }

  override fun actionPerformed(e: AnActionEvent) = tryToCreatePullRequest(e)
}

// NOTE: no need to register in plugin.xml
internal class GHPRCreatePullRequestNotificationAction(
  private val project: Project,
  private val projectMapping: GHGitRepositoryMapping,
  private val account: GithubAccount
) : NotificationAction(
  GithubBundle.message("pull.request.notification.create.action")
) {
  override fun actionPerformed(e: AnActionEvent, notification: Notification) {
    val twVm = project.service<GHPRToolWindowViewModel>()
    val selectorVm = twVm.selectorVm
    selectorVm.selectRepoAndAccount(projectMapping, account)
    selectorVm.submitSelection()

    tryToCreatePullRequest(e)
  }
}

private fun tryToCreatePullRequest(e: AnActionEvent) {
  return e.project!!.service<GHPRToolWindowViewModel>().activateAndAwaitProject {
    createPullRequest()
  }
}