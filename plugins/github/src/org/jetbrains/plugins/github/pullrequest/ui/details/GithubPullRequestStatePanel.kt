// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.details

import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.components.JBOptionButton
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import icons.GithubIcons
import org.jetbrains.plugins.github.api.data.GithubIssueState
import org.jetbrains.plugins.github.pullrequest.data.GithubPullRequestsBusyStateTracker
import org.jetbrains.plugins.github.pullrequest.data.service.GithubPullRequestsSecurityService
import org.jetbrains.plugins.github.pullrequest.data.service.GithubPullRequestsStateService
import org.jetbrains.plugins.github.util.GithubUtil.Delegates.equalVetoingObservable
import java.awt.FlowLayout
import java.awt.event.ActionEvent
import javax.swing.*

internal class GithubPullRequestStatePanel(private val model: GithubPullRequestDetailsModel,
                                           private val securityService: GithubPullRequestsSecurityService,
                                           private val busyStateTracker: GithubPullRequestsBusyStateTracker,
                                           private val stateService: GithubPullRequestsStateService)
  : NonOpaquePanel(VerticalFlowLayout(0, 0)), Disposable {

  private val stateLabel = JLabel().apply {
    border = JBUI.Borders.empty(UIUtil.DEFAULT_VGAP, 0)
  }
  private val accessDeniedPanel = JLabel("Repository write access required to merge pull requests").apply {
    foreground = UIUtil.getContextHelpForeground()
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

  private val mergeAction = object : AbstractAction("Merge...") {
    override fun actionPerformed(e: ActionEvent?) {
      state?.run { stateService.merge(number) }
    }
  }
  private val rebaseMergeAction = object : AbstractAction("Rebase and Merge") {
    override fun actionPerformed(e: ActionEvent?) {
      state?.run { stateService.rebaseMerge(number) }
    }
  }
  private val squashMergeAction = object : AbstractAction("Squash and Merge...") {
    override fun actionPerformed(e: ActionEvent?) {
      state?.run { stateService.squashMerge(number) }
    }
  }
  private val mergeButton = JBOptionButton(null, null)

  private val browseButton = LinkLabel.create("Open on GitHub") {
    model.details?.run { BrowserUtil.browse(htmlUrl) }
  }.apply {
    icon = AllIcons.Ide.External_link_arrow
    setHorizontalTextPosition(SwingConstants.LEFT)
  }

  private val buttonsPanel = NonOpaquePanel(FlowLayout(FlowLayout.LEADING, 0, 0)).apply {
    border = JBUI.Borders.empty(UIUtil.DEFAULT_VGAP, 0)

    if (Registry.`is`("github.action.pullrequest.state.useapi")) {
      add(mergeButton)
      add(closeButton)
      add(reopenButton)
    }
    else {
      add(browseButton)
    }
  }

  init {
    isOpaque = false
    add(stateLabel)
    add(accessDeniedPanel)
    add(buttonsPanel)

    model.addDetailsChangedListener(this) {
      state = model.details?.let {
        GithubPullRequestStatePanel.State(it.number, it.state, it.merged, it.mergeable, it.rebaseable,
                                          securityService.isCurrentUserWithPushAccess(), securityService.isCurrentUser(it.user),
                                          securityService.isMergeAllowed(),
                                          securityService.isRebaseMergeAllowed(),
                                          securityService.isSquashMergeAllowed(),
                                          securityService.isMergeForbiddenForProject(),
                                          busyStateTracker.isBusy(it.number))
      }
    }

    busyStateTracker.addPullRequestBusyStateListener(this) {
      if (it == state?.number)
        state = state?.copy(busy = busyStateTracker.isBusy(it))
    }
  }

  private var state: GithubPullRequestStatePanel.State? by equalVetoingObservable<GithubPullRequestStatePanel.State?>(null) {
    updateText(it)
    updateActions(it)
  }

  private fun updateText(state: GithubPullRequestStatePanel.State?) {

    if (state == null) {
      stateLabel.text = ""
      stateLabel.icon = null

      accessDeniedPanel.isVisible = false
    }
    else {
      if (state.state == GithubIssueState.closed) {
        if (state.merged) {
          stateLabel.icon = GithubIcons.PullRequestClosed
          stateLabel.text = "Pull request is merged"
        }
        else {
          stateLabel.icon = GithubIcons.PullRequestClosed
          stateLabel.text = "Pull request is closed"
        }
        accessDeniedPanel.isVisible = false
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
        accessDeniedPanel.isVisible = !state.editAllowed
      }
    }
  }

  private fun updateActions(state: GithubPullRequestStatePanel.State?) {
    if (state == null) {
      reopenAction.isEnabled = false
      reopenButton.isVisible = false

      closeAction.isEnabled = false
      closeButton.isVisible = false

      mergeAction.isEnabled = false
      rebaseMergeAction.isEnabled = false
      squashMergeAction.isEnabled = false
      mergeButton.action = null
      mergeButton.options = emptyArray()
      mergeButton.isVisible = false

      browseButton.isVisible = false
    }
    else {
      reopenButton.isVisible = (state.editAllowed || state.currentUserIsAuthor) && state.state == GithubIssueState.closed && !state.merged
      reopenAction.isEnabled = reopenButton.isVisible && !state.busy

      closeButton.isVisible = (state.editAllowed || state.currentUserIsAuthor) && state.state == GithubIssueState.open
      closeAction.isEnabled = closeButton.isVisible && !state.busy

      mergeButton.isVisible = state.editAllowed && state.state == GithubIssueState.open && !state.merged
      mergeAction.isEnabled = mergeButton.isVisible && (state.mergeable ?: false) && !state.busy && !state.mergeForbidden
      rebaseMergeAction.isEnabled = mergeButton.isVisible && (state.rebaseable ?: false) && !state.busy && !state.mergeForbidden
      squashMergeAction.isEnabled = mergeButton.isVisible && (state.mergeable ?: false) && !state.busy && !state.mergeForbidden

      mergeButton.optionTooltipText = if (state.mergeForbidden) "Merge actions are disabled for this project" else null

      val allowedActions = mutableListOf<Action>()
      if (state.mergeAllowed) allowedActions.add(mergeAction)
      if (state.rebaseMergeAllowed) allowedActions.add(rebaseMergeAction)
      if (state.squashMergeAllowed) allowedActions.add(squashMergeAction)

      val action = allowedActions.firstOrNull()
      val actions = if (allowedActions.size > 1) Array(allowedActions.size - 1) { allowedActions[it + 1] } else emptyArray()
      mergeButton.action = action
      mergeButton.options = actions

      browseButton.isVisible = true
    }
  }

  override fun dispose() {}

  private data class State(val number: Long,
                           val state: GithubIssueState,
                           val merged: Boolean,
                           val mergeable: Boolean?,
                           val rebaseable: Boolean?,
                           val editAllowed: Boolean,
                           val currentUserIsAuthor: Boolean,
                           val mergeAllowed: Boolean,
                           val rebaseMergeAllowed: Boolean,
                           val squashMergeAllowed: Boolean,
                           val mergeForbidden: Boolean,
                           val busy: Boolean)
}