// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.details

import com.intellij.icons.AllIcons
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.components.BorderLayoutPanel
import icons.GithubIcons
import org.jetbrains.plugins.github.api.data.GithubAuthenticatedUser
import org.jetbrains.plugins.github.api.data.GithubIssueState
import org.jetbrains.plugins.github.api.data.GithubPullRequestDetailed
import org.jetbrains.plugins.github.api.data.GithubRepoWithPermissions
import org.jetbrains.plugins.github.pullrequest.data.service.GithubPullRequestsStateService
import org.jetbrains.plugins.github.util.GithubUtil.Delegates.equalVetoingObservable
import java.awt.FlowLayout
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.JButton
import javax.swing.JLabel

internal class GithubPullRequestStatePanel(private val stateService: GithubPullRequestsStateService)
  : BorderLayoutPanel() {

  private val stateLabel = JLabel().apply {
    border = JBUI.Borders.empty(UIUtil.DEFAULT_VGAP, 0)
  }

  private val closeAction = object : AbstractAction("Close") {
    override fun actionPerformed(e: ActionEvent?) {
      state?.run { stateService.close(number) }
    }
  }
  private val closeButton = JButton(closeAction)

  private val reopenAction = object : AbstractAction("Reopen") {
    override fun actionPerformed(e: ActionEvent?) {
      state?.run { stateService.reopen(number) }
    }
  }
  private val reopenButton = JButton(reopenAction)

  private val buttonsPanel = NonOpaquePanel(FlowLayout(FlowLayout.LEADING, 0, 0)).apply {
    border = JBUI.Borders.empty(UIUtil.DEFAULT_VGAP, 0)

    add(closeButton)
    add(reopenButton)
  }

  var state: GithubPullRequestStatePanel.State? by equalVetoingObservable<GithubPullRequestStatePanel.State?>(null) {
    updateText(state)
    updateActions(state)
  }

  private fun updateText(state: GithubPullRequestStatePanel.State?) {
    stateLabel.text = ""
    stateLabel.icon = null

    if (state != null) {
      if (state.state == GithubIssueState.closed) {
        if (state.merged) {
          stateLabel.icon = GithubIcons.PullRequestClosed
          stateLabel.text = "Pull request is merged"
        }
        else {
          stateLabel.icon = GithubIcons.PullRequestClosed
          stateLabel.text = "Pull request is closed"
        }
      }
      else {
        val mergeable = state.mergeable
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

  private fun updateActions(state: GithubPullRequestStatePanel.State?) {
    if (state == null) {
      reopenAction.isEnabled = false
      reopenButton.isVisible = false

      closeAction.isEnabled = false
      closeButton.isVisible = false
    }
    else {
      val busy = stateService.isBusy(state.number)

      reopenButton.isVisible = state.editAllowed && state.state == GithubIssueState.closed && !state.merged
      reopenAction.isEnabled = reopenButton.isVisible && !busy

      closeButton.isVisible = (state.editAllowed || state.currentUserIsAuthor) && state.state == GithubIssueState.open
      closeAction.isEnabled = closeButton.isVisible && !busy
    }
  }

  init {
    isOpaque = false
    addToTop(stateLabel)
    addToCenter(buttonsPanel)
  }

  data class State(val number: Long, val state: GithubIssueState, val merged: Boolean, val mergeable: Boolean?, val rebaseable: Boolean?,
                   val editAllowed: Boolean, val currentUserIsAuthor: Boolean,
                   val busy: Boolean) {
    companion object {
      fun create(user: GithubAuthenticatedUser,
                 repo: GithubRepoWithPermissions,
                 details: GithubPullRequestDetailed,
                 busy: Boolean) = details.let {
        State(it.number, it.state, it.merged, it.mergeable, it.rebaseable,
              repo.permissions.isAdmin || repo.permissions.isPush, it.user == user,
              busy)
      }
    }
  }
}