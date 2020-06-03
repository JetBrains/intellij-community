// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.github.pullrequest.action

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAwareAction
import git4idea.branch.GitBranchUiHandlerImpl
import git4idea.branch.GitBranchUtil
import git4idea.branch.GitBranchWorker
import git4idea.commands.Git
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.util.GithubAsyncUtil

class GHPRCreateBranchAction : DumbAwareAction(GithubBundle.messagePointer("pull.request.branch.checkout.create.action"),
                                               GithubBundle.messagePointer("pull.request.branch.checkout.create.action.description"),
                                               null) {

  override fun update(e: AnActionEvent) {
    val project = e.getData(CommonDataKeys.PROJECT)
    val repository = e.getData(GHPRActionKeys.GIT_REPOSITORY)
    val selection = e.getData(GHPRActionKeys.PULL_REQUEST_DATA_PROVIDER)
    e.presentation.isEnabled = project != null && !project.isDefault && selection != null && repository != null
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.getRequiredData(CommonDataKeys.PROJECT)
    val repository = e.getRequiredData(GHPRActionKeys.GIT_REPOSITORY)
    val dataProvider = e.getRequiredData(GHPRActionKeys.PULL_REQUEST_DATA_PROVIDER)

    val pullRequestNumber = dataProvider.id.number
    val options = GitBranchUtil.getNewBranchNameFromUser(project, listOf(repository),
                                                         GithubBundle.message("pull.request.branch.checkout.create.dialog.title",
                                                                              pullRequestNumber),
                                                         "pull/${pullRequestNumber}") ?: return

    if (!options.checkout) {
      object : Task.Backgroundable(project, GithubBundle.message("pull.request.branch.checkout.create.task.title"), true) {
        private val git = Git.getInstance()

        override fun run(indicator: ProgressIndicator) {
          val sha = GithubAsyncUtil.awaitFuture(indicator, dataProvider.detailsData.loadDetails()).headRefOid
          GithubAsyncUtil.awaitFuture(indicator, dataProvider.changesData.fetchHeadBranch())

          indicator.text = GithubBundle.message("pull.request.branch.checkout.create.task.indicator")
          GitBranchWorker(project, git, GitBranchUiHandlerImpl(project, git, indicator))
            .createBranch(options.name, mapOf(repository to sha))
        }
      }.queue()
    }
    else {
      object : Task.Backgroundable(project, GithubBundle.message("pull.request.branch.checkout.task.title"), true) {
        private val git = Git.getInstance()

        override fun run(indicator: ProgressIndicator) {
          val sha = GithubAsyncUtil.awaitFuture(indicator, dataProvider.detailsData.loadDetails()).headRefOid
          GithubAsyncUtil.awaitFuture(indicator, dataProvider.changesData.fetchHeadBranch())

          indicator.text = GithubBundle.message("pull.request.branch.checkout.task.indicator")
          GitBranchWorker(project, git, GitBranchUiHandlerImpl(project, git, indicator))
            .checkoutNewBranchStartingFrom(options.name, sha, listOf(repository))
        }
      }.queue()
    }
  }
}