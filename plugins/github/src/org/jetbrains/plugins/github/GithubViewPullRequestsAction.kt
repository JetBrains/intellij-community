// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.PerformInBackgroundOption
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import git4idea.repo.GitRemote
import git4idea.repo.GitRepository
import org.jetbrains.plugins.github.api.GithubApiRequestExecutorManager
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.pullrequest.GithubPullRequestsToolWindowManager
import org.jetbrains.plugins.github.pullrequest.data.GHPullRequestsDataContext
import org.jetbrains.plugins.github.pullrequest.data.GHPullRequestsDataContextRepository
import org.jetbrains.plugins.github.util.GitRemoteUrlCoordinates
import org.jetbrains.plugins.github.util.GithubNotifications
import org.jetbrains.plugins.github.util.GithubUrlUtil

class GithubViewPullRequestsAction : AbstractGithubUrlGroupingAction("View Pull Requests", null, AllIcons.Vcs.Vendors.Github) {

  override fun actionPerformed(e: AnActionEvent,
                               project: Project,
                               repository: GitRepository,
                               remote: GitRemote,
                               remoteUrl: String,
                               account: GithubAccount) {
    val fullPath = GithubUrlUtil.getUserAndRepositoryFromRemoteUrl(remoteUrl)
    if (fullPath == null) {
      GithubNotifications.showError(project, "Invalid GitHub Repository URL", "$remoteUrl is not a GitHub repository.")
      return
    }

    val requestExecutor = service<GithubApiRequestExecutorManager>().getExecutor(account, project) ?: return

    val toolWindowManager = project.service<GithubPullRequestsToolWindowManager>()
    val remoteCoordinates = GitRemoteUrlCoordinates(remoteUrl, remote, repository)
    if (toolWindowManager.showPullRequestsTabIfExists(remoteCoordinates, account)) return

    object : Task.Backgroundable(project, "Loading GitHub Repository Information", true, PerformInBackgroundOption.DEAF) {
      lateinit var context: GHPullRequestsDataContext

      override fun run(indicator: ProgressIndicator) {
        context = project.service<GHPullRequestsDataContextRepository>().getContext(indicator, account, requestExecutor, remoteCoordinates)
      }

      override fun onSuccess() {
        toolWindowManager.createPullRequestsTab(context)
        toolWindowManager.showPullRequestsTabIfExists(remoteCoordinates, account)
      }

      override fun onThrowable(error: Throwable) {
        GithubNotifications.showError(project, "Failed To Load Repository Information", error)
      }
    }.queue()
  }
}