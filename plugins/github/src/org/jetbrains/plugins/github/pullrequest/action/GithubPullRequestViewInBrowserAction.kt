// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.github.pullrequest.action

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction

class GithubPullRequestViewInBrowserAction : DumbAwareAction("View on GitHub",
                                                             "Open in browser",
                                                             null) {
  override fun update(e: AnActionEvent) {
    val pullRequest = e.getData(GithubPullRequestKeys.SELECTED_PULL_REQUEST)
    e.presentation.isEnabled = pullRequest != null && pullRequest.pullRequestLinks != null
  }

  override fun actionPerformed(e: AnActionEvent) {
    val pullRequest = e.getRequiredData(GithubPullRequestKeys.SELECTED_PULL_REQUEST)
    pullRequest.pullRequestLinks!!.run { BrowserUtil.browse(htmlUrl) }
  }
}