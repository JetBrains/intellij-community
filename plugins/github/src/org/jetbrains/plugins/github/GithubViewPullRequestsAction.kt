// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import git4idea.repo.GitRemote
import git4idea.repo.GitRepository
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.pullrequest.GithubPullRequestsToolWindowManager

class GithubViewPullRequestsAction : AbstractGithubUrlGroupingAction("View Pull Requests", null, AllIcons.Vcs.Vendors.Github) {
  override fun actionPerformed(e: AnActionEvent,
                               project: Project,
                               repository: GitRepository,
                               remote: GitRemote,
                               remoteUrl: String,
                               account: GithubAccount) {
    project.service<GithubPullRequestsToolWindowManager>().showPullRequestsTab(repository, remote, remoteUrl, account)
  }
}