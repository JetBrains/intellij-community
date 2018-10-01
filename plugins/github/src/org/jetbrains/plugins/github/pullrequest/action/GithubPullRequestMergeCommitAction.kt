// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.action

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.api.GithubApiRequests
import org.jetbrains.plugins.github.api.data.GithubPullRequestDetailed
import org.jetbrains.plugins.github.pullrequest.action.merge.GithubPullRequestMergeActionBase
import org.jetbrains.plugins.github.pullrequest.action.ui.GithubMergeCommitMessageDialog
import org.jetbrains.plugins.github.util.GithubNotifications

class GithubPullRequestMergeCommitAction : GithubPullRequestMergeActionBase("Create a Merge Commit...") {
  override fun canMerge(details: GithubPullRequestDetailed) = details.mergeable

  override fun mergePullRequest(e: AnActionEvent,
                                project: Project, requestExecutor: GithubApiRequestExecutor, details: GithubPullRequestDetailed) {
    val dialog = GithubMergeCommitMessageDialog(project,
                                                "Merge Pull Request",
                                                "Merge pull request #${details.number} from ${details.head.label}",
                                                details.title)
    if (!dialog.showAndGet()) return

    val commitMessage = dialog.message

    object : Task.Backgroundable(project, "Merging Pull Request", true) {
      override fun run(indicator: ProgressIndicator) {
        requestExecutor.execute(indicator,
                                GithubApiRequests.Repos.PullRequests.merge(details,
                                                                           commitMessage.first, commitMessage.second,
                                                                           details.head.sha))
      }

      override fun onSuccess() {
        GithubNotifications.showInfo(project, "Pull Request Merged", "Successfully merged pull request #${details.number}")
      }

      override fun onThrowable(error: Throwable) {
        GithubNotifications.showError(project, "Failed To Merge Pull Request", error)
      }

      override fun onFinished() {
        e.getData(GithubPullRequestKeys.PULL_REQUESTS_COMPONENT)?.refreshPullRequest(details.number)
      }
    }.queue()
  }
}
