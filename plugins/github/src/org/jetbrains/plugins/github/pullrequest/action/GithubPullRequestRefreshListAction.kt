// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.github.pullrequest.action

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction

class GithubPullRequestRefreshListAction : DumbAwareAction("Refresh List", null, AllIcons.Actions.Refresh) {
  override fun update(e: AnActionEvent) {
    val component = e.getData(GithubPullRequestKeys.PULL_REQUESTS_COMPONENT)
    e.presentation.isEnabled = component != null
  }

  override fun actionPerformed(e: AnActionEvent) {
    e.getRequiredData(GithubPullRequestKeys.PULL_REQUESTS_COMPONENT).refreshAllPullRequests()
  }
}