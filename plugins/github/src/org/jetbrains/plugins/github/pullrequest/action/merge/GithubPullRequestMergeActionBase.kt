// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.action.merge

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.api.data.GithubIssueState
import org.jetbrains.plugins.github.api.data.GithubPullRequestDetailed
import org.jetbrains.plugins.github.pullrequest.action.GithubPullRequestKeys
import org.jetbrains.plugins.github.util.GithubAsyncUtil
import org.jetbrains.plugins.github.util.GithubNotifications

abstract class GithubPullRequestMergeActionBase(text: String) : DumbAwareAction(text) {
  override fun update(e: AnActionEvent) {
    //TODO: project-level switch
    val permissions = e.getData(GithubPullRequestKeys.REPO_DETAILS)?.permissions
    e.presentation.isEnabledAndVisible = permissions != null && (permissions.isPush || permissions.isAdmin) && isEnabled(e)
  }

  private fun isEnabled(e: AnActionEvent): Boolean {
    val pullRequest = e.getData(GithubPullRequestKeys.SELECTED_PULL_REQUEST) ?: return false
    return pullRequest.state == GithubIssueState.open
  }

  final override fun actionPerformed(e: AnActionEvent) {
    val project = e.getRequiredData(CommonDataKeys.PROJECT)
    val detailsRequest = e.getRequiredData(GithubPullRequestKeys.SELECTED_PULL_REQUEST_DATA_PROVIDER).detailsRequest

    GithubAsyncUtil.awaitFutureAndRunOnEdt(detailsRequest,
                                           project, "Loading Pull Request Details", "Failed to Load Pull Request Details") { details ->
      if (!preCheck(project, details)) return@awaitFutureAndRunOnEdt
      mergePullRequest(e, project, e.getRequiredData(GithubPullRequestKeys.API_REQUEST_EXECUTOR), details)
    }
  }

  private fun preCheck(project: Project, details: GithubPullRequestDetailed): Boolean {
    if (details.merged) {
      GithubNotifications.showError(project, "Failed to Merge Pull Request", "Pull request #${details.number} is already merged")
      return false
    }
    if (!canMerge(details)) {
      GithubNotifications.showError(project, "Failed to Merge Pull Request",
                                    "Cannot merge pull request #${details.number} due to conflicts")
      return false
    }

    return true
  }

  protected abstract fun canMerge(details: GithubPullRequestDetailed): Boolean

  protected abstract fun mergePullRequest(e: AnActionEvent,
                                          project: Project, requestExecutor: GithubApiRequestExecutor, details: GithubPullRequestDetailed)
}