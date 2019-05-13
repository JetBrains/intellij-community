// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.action.edit

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import org.jetbrains.plugins.github.api.data.GithubIssueState
import org.jetbrains.plugins.github.pullrequest.action.GithubPullRequestKeys

abstract class GithubPullRequestStateChangeAction(text: String) : DumbAwareAction(text) {
  final override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = isEnabledAndVisible(e)
  }

  private fun isEnabledAndVisible(e: AnActionEvent): Boolean {
    e.project ?: return false
    e.getData(GithubPullRequestKeys.API_REQUEST_EXECUTOR) ?: return false
    e.getData(GithubPullRequestKeys.SELECTED_PULL_REQUEST_DATA_PROVIDER) ?: return false

    val pullRequest = e.getData(GithubPullRequestKeys.SELECTED_PULL_REQUEST) ?: return false
    if (requiredState != pullRequest.state) return false

    val permissions = e.getData(GithubPullRequestKeys.REPO_DETAILS)?.permissions ?: return false
    if (permissions.isPush || permissions.isAdmin) return true

    val currentUser = e.getData(GithubPullRequestKeys.ACCOUNT_DETAILS) ?: return false
    return pullRequest.user == currentUser
  }

  abstract val requiredState: GithubIssueState
}