// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.github.pullrequest.action

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction

class GithubPullRequestRefreshCommentsAction : DumbAwareAction("Refresh Pull Request Comments", null, AllIcons.Actions.Refresh) {
  override fun update(e: AnActionEvent) {
    val selection = e.getData(GithubPullRequestKeys.DATA_CONTEXT)?.selectedPullRequestDataProvider
    e.presentation.isEnabled = selection != null
  }

  override fun actionPerformed(e: AnActionEvent) {
    e.getRequiredData(GithubPullRequestKeys.DATA_CONTEXT).selectedPullRequestDataProvider?.reloadComments()
  }
}