// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.details

import com.intellij.icons.AllIcons
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import icons.GithubIcons
import org.jetbrains.plugins.github.api.data.GithubIssueState
import org.jetbrains.plugins.github.api.data.GithubPullRequestDetailed
import org.jetbrains.plugins.github.util.GithubUtil.Delegates.equalVetoingObservable
import javax.swing.JLabel

internal class GithubPullRequestStatePanel : NonOpaquePanel() {
  private val stateLabel = JLabel().apply {
    border = JBUI.Borders.empty(UIUtil.DEFAULT_VGAP, 0)
  }
  var state: GithubPullRequestStatePanel.State? by equalVetoingObservable<GithubPullRequestStatePanel.State?>(null) {
    stateLabel.text = ""
    stateLabel.icon = null
    if (it != null) {
      if (it.state == GithubIssueState.closed) {
        if (it.merged) {
          stateLabel.icon = GithubIcons.PullRequestClosed
          stateLabel.text = "Pull request is merged"
        }
        else {
          stateLabel.icon = GithubIcons.PullRequestClosed
          stateLabel.text = "Pull request is closed"
        }
      }
      else {
        val mergeable = it.mergeable
        if (mergeable == null) {
          stateLabel.icon = AllIcons.RunConfigurations.TestNotRan
          stateLabel.text = "Checking for ability to merge automatically..."
        }
        else {
          if (mergeable) {
            stateLabel.icon = AllIcons.RunConfigurations.TestPassed
            stateLabel.text = "Branch has no conflicts with base branch"
          }
          else {
            stateLabel.icon = AllIcons.RunConfigurations.TestFailed
            stateLabel.text = "Branch has conflicts that must be resolved"
          }
        }
      }
    }
  }

  init {
    setContent(stateLabel)
  }

  data class State(val state: GithubIssueState, val merged: Boolean, val mergeable: Boolean?, val rebaseable: Boolean?) {
    companion object {
      fun fromDetails(details: GithubPullRequestDetailed?) = details?.let {
        State(it.state, it.merged, it.mergeable, it.rebaseable)
      }
    }
  }
}