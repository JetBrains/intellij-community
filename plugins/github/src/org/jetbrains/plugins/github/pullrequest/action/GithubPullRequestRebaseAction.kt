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
import org.jetbrains.plugins.github.util.GithubNotifications

class GithubPullRequestRebaseAction : GithubPullRequestMergeActionBase("Rebase and Merge") {
  override fun canMerge(details: GithubPullRequestDetailed) = details.rebaseable

  override fun mergePullRequest(e: AnActionEvent,
                                project: Project, requestExecutor: GithubApiRequestExecutor, details: GithubPullRequestDetailed) {
    object : Task.Backgroundable(project, "Rebasing and Merging Pull Request", true) {
      override fun run(indicator: ProgressIndicator) {
        requestExecutor.execute(indicator,
                                GithubApiRequests.Repos.PullRequests.rebaseMerge(details, details.head.sha))
      }

      override fun onSuccess() {
        GithubNotifications.showInfo(project, "Pull Request Rebased and Merged",
                                     "Successfully rebased and merged pull request #${details.number}")
      }

      override fun onThrowable(error: Throwable) {
        GithubNotifications.showError(project, "Failed To Rebase and Merge Pull Request", error)
      }

      override fun onFinished() {
        e.getData(GithubPullRequestKeys.PULL_REQUESTS_COMPONENT)?.refreshPullRequest(details.number)
      }
    }.queue()
  }
}