// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import org.jetbrains.plugins.github.api.data.GHPullRequest
import org.jetbrains.plugins.github.api.data.GHPullRequestMergeableState
import org.jetbrains.plugins.github.api.data.GHPullRequestState
import org.jetbrains.plugins.github.pullrequest.data.GithubPullRequestsBusyStateTracker
import org.jetbrains.plugins.github.pullrequest.data.service.GithubPullRequestsSecurityService
import org.jetbrains.plugins.github.pullrequest.data.service.GithubPullRequestsStateService
import org.jetbrains.plugins.github.ui.util.SingleValueModel
import org.jetbrains.plugins.github.util.GithubUtil.Delegates.equalVetoingObservable
import java.awt.FlowLayout
import java.awt.event.ActionEvent
import javax.swing.*

internal class GithubPullRequestStatePanel(private val model: SingleValueModel<GHPullRequest?>,
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
    model.value?.run { BrowserUtil.browse(url) }
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

  private var state: State? by equalVetoingObservable<State?>(null) {
    updateText(it)
    updateActions(it)
  }

  init {
    isOpaque = false
    add(stateLabel)
    add(accessDeniedPanel)
    add(buttonsPanel)

    fun update() {
      state = model.value?.let {
        State(it.number, it.state, GHPullRequestMergeableState.MERGEABLE,
              it.viewerCanUpdate, it.viewerDidAuthor,
              securityService.isMergeAllowed(),
              securityService.isRebaseMergeAllowed(),
              securityService.isSquashMergeAllowed(),
              securityService.isMergeForbiddenForProject(),
              busyStateTracker.isBusy(it.number))
      }
    }

    model.addValueChangedListener(this) {
      update()
    }
    update()

    busyStateTracker.addPullRequestBusyStateListener(this) {
      if (it == state?.number)
        state = state?.copy(busy = busyStateTracker.isBusy(it))
    }
  }

  private fun updateText(state: State?) {

    if (state == null) {
      stateLabel.text = ""
      stateLabel.icon = null

      accessDeniedPanel.isVisible = false
    }
    else {
      when (state.state) {
        GHPullRequestState.OPEN -> {
          when (state.mergeable) {
            GHPullRequestMergeableState.MERGEABLE -> {
              stateLabel.icon = AllIcons.RunConfigurations.TestPassed
              stateLabel.text = "Branch has no conflicts with base branch"
            }
            GHPullRequestMergeableState.CONFLICTING -> {
              stateLabel.icon = AllIcons.RunConfigurations.TestFailed
              stateLabel.text = "Branch has conflicts that must be resolved"
            }
            GHPullRequestMergeableState.UNKNOWN -> {
              stateLabel.icon = AllIcons.RunConfigurations.TestNotRan
              stateLabel.text = "Checking for ability to merge automatically..."
            }
          }
          accessDeniedPanel.isVisible = !state.editAllowed
        }
        GHPullRequestState.CLOSED -> {
          stateLabel.icon = GithubIcons.PullRequestClosed
          stateLabel.text = "Pull request is closed"
          accessDeniedPanel.isVisible = false
        }
        GHPullRequestState.MERGED -> {
          stateLabel.icon = GithubIcons.PullRequestMerged
          stateLabel.text = "Pull request is merged"
          accessDeniedPanel.isVisible = false
        }
      }
    }
  }

  private fun updateActions(state: State?) {
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
      reopenButton.isVisible = (state.editAllowed || state.currentUserIsAuthor) && state.state == GHPullRequestState.CLOSED
      reopenAction.isEnabled = reopenButton.isVisible && !state.busy

      closeButton.isVisible = (state.editAllowed || state.currentUserIsAuthor) && state.state == GHPullRequestState.OPEN
      closeAction.isEnabled = closeButton.isVisible && !state.busy

      mergeButton.isVisible = state.editAllowed && state.state == GHPullRequestState.OPEN
      val mergeable = mergeButton.isVisible && state.mergeable == GHPullRequestMergeableState.MERGEABLE && !state.busy && !state.mergeForbidden
      mergeAction.isEnabled = mergeable
      rebaseMergeAction.isEnabled = mergeable
      squashMergeAction.isEnabled = mergeable

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
                           val state: GHPullRequestState,
                           val mergeable: GHPullRequestMergeableState,
                           val editAllowed: Boolean,
                           val currentUserIsAuthor: Boolean,
                           val mergeAllowed: Boolean,
                           val rebaseMergeAllowed: Boolean,
                           val squashMergeAllowed: Boolean,
                           val mergeForbidden: Boolean,
                           val busy: Boolean)
}