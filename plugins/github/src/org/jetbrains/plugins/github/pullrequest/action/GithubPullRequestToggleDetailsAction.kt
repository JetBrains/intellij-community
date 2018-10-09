// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.github.pullrequest.action

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareToggleAction
import org.jetbrains.plugins.github.pullrequest.config.GithubPullRequestsUISettings

class GithubPullRequestToggleDetailsAction
  : DumbAwareToggleAction("Show Details", "Display details panel", AllIcons.Actions.PreviewDetailsVertically) {

  override fun isSelected(e: AnActionEvent): Boolean = service<GithubPullRequestsUISettings>().showDetails

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    service<GithubPullRequestsUISettings>().showDetails = state
  }
}