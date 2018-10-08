// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.action

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.api.GithubApiRequests
import org.jetbrains.plugins.github.api.data.GithubPullRequestDetailed
import org.jetbrains.plugins.github.pullrequest.action.merge.GithubPullRequestMergeActionBase
import org.jetbrains.plugins.github.pullrequest.action.ui.GithubMergeCommitMessageDialog
import org.jetbrains.plugins.github.util.GithubAsyncUtil
import org.jetbrains.plugins.github.util.GithubNotifications

class GithubPullRequestSquashAction : GithubPullRequestMergeActionBase("Squash and Merge...") {
  override fun canMerge(details: GithubPullRequestDetailed) = details.mergeable

  override fun mergePullRequest(e: AnActionEvent,
                                project: Project, requestExecutor: GithubApiRequestExecutor, details: GithubPullRequestDetailed) {
    val commitsRequest = e.getRequiredData(GithubPullRequestKeys.SELECTED_PULL_REQUEST_DATA_PROVIDER).logCommitsRequest

    GithubAsyncUtil.awaitFutureAndRunOnEdt(commitsRequest,
                                           project, "Loading Pull Request Commits", "Failed to Load Pull Request Commits") { commits ->
      val body = "* " + StringUtil.join(commits, { it.subject }, "\n\n* ")
      val dialog = GithubMergeCommitMessageDialog(project,
                                                  "Merge Pull Request",
                                                  "${details.title} (#${details.number})",
                                                  body)
      if (!dialog.showAndGet()) return@awaitFutureAndRunOnEdt

      val commitMessage = dialog.message

      object : Task.Backgroundable(project, "Squashing and Merging Pull Request", true) {
        override fun run(indicator: ProgressIndicator) {
          requestExecutor.execute(indicator,
                                  GithubApiRequests.Repos.PullRequests.squashMerge(details,
                                                                                   commitMessage.first, commitMessage.second,
                                                                                   details.head.sha))
        }

        override fun onSuccess() {
          GithubNotifications.showInfo(project, "Pull Request Squashed and Merged",
                                       "Successfully squashed amd merged pull request #${details.number}")
        }

        override fun onThrowable(error: Throwable) {
          GithubNotifications.showError(project, "Failed To Squash and Merge Pull Request", error)
        }

        override fun onFinished() {
          e.getData(GithubPullRequestKeys.PULL_REQUESTS_COMPONENT)?.refreshPullRequest(details.number)
        }
      }.queue()
    }
  }
}