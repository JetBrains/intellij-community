// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.details

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.ui.components.JBOptionButton
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import icons.GithubIcons
import org.jetbrains.plugins.github.api.data.GHRepositoryPermissionLevel
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequest
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestMergeableState
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestShort
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestState
import org.jetbrains.plugins.github.pullrequest.data.GithubPullRequestsBusyStateTracker
import org.jetbrains.plugins.github.pullrequest.data.service.GithubPullRequestsSecurityService
import org.jetbrains.plugins.github.pullrequest.data.service.GithubPullRequestsStateService
import org.jetbrains.plugins.github.ui.util.SingleValueModel
import java.awt.FlowLayout
import java.awt.event.ActionEvent
import javax.swing.*

object GHPRStatePanel {
  fun create(model: SingleValueModel<out GHPullRequestShort?>,
             securityService: GithubPullRequestsSecurityService,
             busyStateTracker: GithubPullRequestsBusyStateTracker,
             stateService: GithubPullRequestsStateService,
             parentDisposable: Disposable): JComponent {

    val stateLabel = JLabel().apply {
      border = JBUI.Borders.empty(UIUtil.DEFAULT_VGAP, 0)
    }
    val accessDeniedLabel = JLabel("Repository write access required to merge pull requests").apply {
      foreground = UIUtil.getContextHelpForeground()
    }
    val buttonsPanel = createButtons(model, securityService, busyStateTracker, stateService, parentDisposable).apply {
      border = JBUI.Borders.empty(UIUtil.DEFAULT_VGAP, 0)
    }

    Controller(model, securityService, stateLabel, accessDeniedLabel)

    return NonOpaquePanel(VerticalFlowLayout(0, 0)).apply {
      add(stateLabel)
      add(accessDeniedLabel)
      add(buttonsPanel)
    }
  }

  private fun createButtons(model: SingleValueModel<out GHPullRequestShort?>,
                            securityService: GithubPullRequestsSecurityService,
                            busyStateTracker: GithubPullRequestsBusyStateTracker,
                            stateService: GithubPullRequestsStateService,
                            parentDisposable: Disposable): JComponent {
    val closeButton = JButton()
    val reopenButton = JButton()

    val mergeButton = JBOptionButton(null, null)

    ButtonsController(model, securityService, busyStateTracker, stateService, parentDisposable,
                      closeButton, reopenButton, mergeButton)

    return NonOpaquePanel(FlowLayout(FlowLayout.LEADING, 0, 0)).apply {
        add(mergeButton)
        add(closeButton)
        add(reopenButton)
    }
  }

  private class Controller(private val model: SingleValueModel<out GHPullRequestShort?>,
                           private val securityService: GithubPullRequestsSecurityService,
                           private val stateLabel: JLabel,
                           private val accessDeniedLabel: JLabel) {

    init {
      updateText()

      model.addValueChangedListener {
        updateText()
      }
    }

    private fun updateText() {
      val value = model.value
      if (value == null) {
        stateLabel.text = ""
        stateLabel.icon = null

        accessDeniedLabel.isVisible = false
      }
      else {
        when (value.state) {
          GHPullRequestState.OPEN -> {
            if (value is GHPullRequest)
              when (value.mergeable) {
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
            else {
              stateLabel.icon = GithubIcons.PullRequestOpen
              stateLabel.text = "Pull request is open"
            }
            accessDeniedLabel.isVisible = !securityService.currentUserHasPermissionLevel(GHRepositoryPermissionLevel.TRIAGE)
          }
          GHPullRequestState.CLOSED -> {
            stateLabel.icon = GithubIcons.PullRequestClosed
            stateLabel.text = "Pull request is closed"
            accessDeniedLabel.isVisible = false
          }
          GHPullRequestState.MERGED -> {
            stateLabel.icon = GithubIcons.PullRequestMerged
            stateLabel.text = "Pull request is merged"
            accessDeniedLabel.isVisible = false
          }
        }
      }
    }
  }

  private class ButtonsController(private val model: SingleValueModel<out GHPullRequestShort?>,
                                  private val securityService: GithubPullRequestsSecurityService,
                                  private val busyStateTracker: GithubPullRequestsBusyStateTracker,
                                  private val stateService: GithubPullRequestsStateService,
                                  parentDisposable: Disposable,
                                  private val closeButton: JButton,
                                  private val reopenButton: JButton,
                                  private val mergeButton: JBOptionButton) {

    val closeAction = object : AbstractAction("Close") {
      override fun actionPerformed(e: ActionEvent?) {
        model.value?.let { stateService.close(it.number) }
      }
    }
    val reopenAction = object : AbstractAction("Reopen") {
      override fun actionPerformed(e: ActionEvent?) {
        model.value?.let { stateService.reopen(it.number) }
      }
    }

    val mergeAction = object : AbstractAction("Merge...") {
      override fun actionPerformed(e: ActionEvent?) {
        model.value?.let { stateService.merge(it.number) }
      }
    }
    val rebaseMergeAction = object : AbstractAction("Rebase and Merge") {
      override fun actionPerformed(e: ActionEvent?) {
        model.value?.let { stateService.rebaseMerge(it.number) }
      }
    }
    val squashMergeAction = object : AbstractAction("Squash and Merge...") {
      override fun actionPerformed(e: ActionEvent?) {
        model.value?.let { stateService.squashMerge(it.number) }
      }
    }

    init {
      updateActions()

      model.addValueChangedListener {
        updateActions()
      }
      busyStateTracker.addPullRequestBusyStateListener(parentDisposable) {
        updateActions()
      }
      closeButton.action = closeAction
      reopenButton.action = reopenAction
    }

    private fun updateActions() {
      val value = model.value
      if (value == null) {
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
      }
      else {
        val canEdit = securityService.currentUserHasPermissionLevel(GHRepositoryPermissionLevel.TRIAGE) ||
                      securityService.currentUser == value.author

        reopenButton.isVisible = canEdit && value.state == GHPullRequestState.CLOSED
        reopenAction.isEnabled = reopenButton.isVisible && !busyStateTracker.isBusy(value.number)

        closeButton.isVisible = canEdit && value.state == GHPullRequestState.OPEN
        closeAction.isEnabled = closeButton.isVisible && !busyStateTracker.isBusy(value.number)

        val canMerge = securityService.currentUserHasPermissionLevel(GHRepositoryPermissionLevel.WRITE)

        mergeButton.isVisible = canMerge && value.state == GHPullRequestState.OPEN && value is GHPullRequest
        val mergeable = mergeButton.isVisible && value is GHPullRequest && value.mergeable == GHPullRequestMergeableState.MERGEABLE &&
                        !busyStateTracker.isBusy(value.number) &&
                        !securityService.isMergeForbiddenForProject()

        mergeAction.isEnabled = mergeable
        rebaseMergeAction.isEnabled = mergeable
        squashMergeAction.isEnabled = mergeable

        mergeButton.optionTooltipText = if (securityService.isMergeForbiddenForProject()) "Merge actions are disabled for this project" else null

        val allowedActions = mutableListOf<Action>()
        if (securityService.isMergeAllowed()) allowedActions.add(mergeAction)
        if (securityService.isRebaseMergeAllowed()) allowedActions.add(rebaseMergeAction)
        if (securityService.isSquashMergeAllowed()) allowedActions.add(squashMergeAction)

        val action = allowedActions.firstOrNull()
        val actions = if (allowedActions.size > 1) Array(allowedActions.size - 1) { allowedActions[it + 1] } else emptyArray()
        mergeButton.action = action
        mergeButton.options = actions
      }
    }
  }
}