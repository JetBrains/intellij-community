// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.notification

import com.intellij.collaboration.ui.notification.CollaborationToolsNotifier
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.project.Project
import com.intellij.platform.util.coroutines.childScope
import git4idea.push.GitPushRepoResult
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.action.GHPRCreatePullRequestNotificationAction
import org.jetbrains.plugins.github.pullrequest.ui.toolwindow.model.GHPRToolWindowViewModel
import org.jetbrains.plugins.github.util.GithubNotificationIdsHolder

@Service(Service.Level.PROJECT)
internal class GHNotificationService(private val project: Project, parentCs: CoroutineScope) {
  private val serviceScope = parentCs.childScope(CoroutineName("GitHub notification service scope"))

  fun showReviewCreationNotification(pushResult: GitPushRepoResult) {
    serviceScope.launch {
      val toolWindowVm = project.serviceAsync<GHPRToolWindowViewModel>()
      val projectVm = toolWindowVm.projectVm.filterNotNull().first()

      val defaultBranch = projectVm.defaultBranch
      if (defaultBranch != null && pushResult.targetBranch.endsWith(defaultBranch)) return@launch

      val existingPullRequest = projectVm.isExistingPullRequest(pushResult) ?: return@launch
      if (!existingPullRequest) {
        notifyReviewCreation(project)
      }
    }
  }

  private fun notifyReviewCreation(project: Project) {
    CollaborationToolsNotifier.getInstance(project).notifyBalloon(
      GithubNotificationIdsHolder.PULL_REQUEST_CREATE,
      GithubBundle.message("pull.request.notification.title"),
      GithubBundle.message("pull.request.notification.create.message"),
      GHPRCreatePullRequestNotificationAction()
    )
  }
}