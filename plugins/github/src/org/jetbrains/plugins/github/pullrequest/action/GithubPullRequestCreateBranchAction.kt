// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.github.pullrequest.action

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.vcs.VcsNotifier
import git4idea.branch.GitBranchUiHandlerImpl
import git4idea.branch.GitBranchUtil
import git4idea.branch.GitBranchWorker
import git4idea.commands.Git
import org.jetbrains.plugins.github.util.GithubAsyncUtil

class GithubPullRequestCreateBranchAction : DumbAwareAction("Create New Local Branch...",
                                                            "Checkout synthetic pull request branch",
                                                            null) {
  override fun update(e: AnActionEvent) {
    val project = e.getData(CommonDataKeys.PROJECT)
    val pullRequest = e.getData(GithubPullRequestKeys.SELECTED_PULL_REQUEST)
    val dataProvider = e.getData(GithubPullRequestKeys.SELECTED_PULL_REQUEST_DATA_PROVIDER)
    e.presentation.isEnabled = project != null && !project.isDefault && pullRequest != null && dataProvider != null
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.getRequiredData(CommonDataKeys.PROJECT)
    val pullRequest = e.getRequiredData(GithubPullRequestKeys.SELECTED_PULL_REQUEST)
    val repository = e.getRequiredData(GithubPullRequestKeys.REPOSITORY)
    val repositoryList = listOf(repository)
    val dataProvider = e.getRequiredData(GithubPullRequestKeys.SELECTED_PULL_REQUEST_DATA_PROVIDER)

    val options = GitBranchUtil.getNewBranchNameFromUser(project, repositoryList,
                                                         "Create New Branch From Pull Request #${pullRequest}",
                                                         "pull/${pullRequest}") ?: return

    if (!options.checkout) {
      object : Task.Backgroundable(project, "Creating Branch From Pull Request", true) {
        private val git = Git.getInstance()
        private val vcsNotifier = project.service<VcsNotifier>()

        override fun run(indicator: ProgressIndicator) {
          val sha = GithubAsyncUtil.awaitFuture(indicator, dataProvider.detailsRequest).head.sha
          GithubAsyncUtil.awaitFuture(indicator, dataProvider.branchFetchRequest)

          indicator.text = "Creating branch"
          GitBranchWorker(project, git, GitBranchUiHandlerImpl(project, git, indicator))
            .createBranch(options.name, mapOf(repository to sha))
        }

        override fun onSuccess() {
          vcsNotifier.notifySuccess("Created Branch ${options.name}")
        }

        override fun onThrowable(error: Throwable) {
          vcsNotifier.notifyError("Failed To Create Branch", error.message.orEmpty())
        }
      }.queue()
    }
    else {
      object : Task.Backgroundable(project, "Checking Out Branch From Pull Request", true) {
        private val git = Git.getInstance()
        private val vcsNotifier = project.service<VcsNotifier>()

        override fun run(indicator: ProgressIndicator) {
          val sha = GithubAsyncUtil.awaitFuture(indicator, dataProvider.detailsRequest).head.sha
          GithubAsyncUtil.awaitFuture(indicator, dataProvider.branchFetchRequest)

          indicator.text = "Checking out branch"
          GitBranchWorker(project, git, GitBranchUiHandlerImpl(project, git, indicator))
            .checkoutNewBranchStartingFrom(options.name, sha, repositoryList)
        }

        override fun onSuccess() {
          vcsNotifier.notifySuccess("Checked Out Branch ${options.name}")
        }

        override fun onThrowable(error: Throwable) {
          vcsNotifier.notifyError("Failed to Checkout Branch", error.message.orEmpty())
        }
      }.queue()
    }
  }
}