// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.action

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.project.DumbAwareAction
import git4idea.branch.GitBranchUtil
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.ui.details.GHPRBranchesPanel
import org.jetbrains.plugins.github.util.GithubGitHelper

class GHPRUpdateBranchAction : DumbAwareAction(GithubBundle.messagePointer("pull.request.branch.update.action"),
                                               GithubBundle.messagePointer("pull.request.branch.update.action.description"),
                                               null) {
  override fun update(e: AnActionEvent) {
    val project = e.getData(CommonDataKeys.PROJECT)
    val repository = e.getData(GHPRActionKeys.GIT_REPOSITORY)
    val selection = e.getData(GHPRActionKeys.PULL_REQUEST_DATA_PROVIDER)
    val loadedDetails = selection?.detailsData?.loadedDetails
    val headRefName = loadedDetails?.headRefName
    val httpUrl = loadedDetails?.headRepository?.url
    val sshUrl = loadedDetails?.headRepository?.sshUrl
    val isFork = loadedDetails?.headRepository?.isFork ?: false

    e.presentation.isEnabled = project != null &&
                               !project.isDefault &&
                               selection != null &&
                               repository != null &&
                               GithubGitHelper.getInstance().findRemote(repository, httpUrl, sshUrl)?.let { remote ->
                                 GithubGitHelper.getInstance().findLocalBranch(repository, remote, isFork, headRefName) != null
                               } ?: false
  }

  override fun actionPerformed(e: AnActionEvent) {
    val repository = e.getRequiredData(GHPRActionKeys.GIT_REPOSITORY)
    val project = repository.project
    val loadedDetails = e.getRequiredData(GHPRActionKeys.PULL_REQUEST_DATA_PROVIDER).detailsData.loadedDetails
    val headRefName = loadedDetails?.headRefName
    val httpUrl = loadedDetails?.headRepository?.url
    val sshUrl = loadedDetails?.headRepository?.sshUrl
    val isFork = loadedDetails?.headRepository?.isFork ?: false
    val prRemote = GithubGitHelper.getInstance().findRemote(repository, httpUrl, sshUrl) ?: return
    val localBranch = GithubGitHelper.getInstance().findLocalBranch(repository, prRemote, isFork, headRefName) ?: return

    GitBranchUtil.updateBranches(project, listOf(repository), listOf(localBranch))
  }
}
