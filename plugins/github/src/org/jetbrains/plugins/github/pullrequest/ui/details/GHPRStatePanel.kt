// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.details

import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.ui.SimpleTextAttributes
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
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataProvider
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRSecurityService
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRStateService
import org.jetbrains.plugins.github.pullrequest.ui.details.action.*
import org.jetbrains.plugins.github.ui.util.HtmlEditorPane
import org.jetbrains.plugins.github.ui.util.SingleValueModel
import java.awt.FlowLayout
import javax.swing.Action
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel

object GHPRStatePanel {
  fun create(project: Project,
             model: SingleValueModel<out GHPullRequestShort>,
             busyStateModel: SingleValueModel<Boolean>,
             dataProvider: GHPRDataProvider,
             securityService: GHPRSecurityService,
             stateService: GHPRStateService): JComponent {

    val stateLabel = JLabel().apply {
      border = JBUI.Borders.empty(UIUtil.DEFAULT_VGAP, 0)
    }
    val accessDeniedLabel = JLabel("Repository write access required to merge pull requests").apply {
      foreground = UIUtil.getContextHelpForeground()
    }
    val buttonsPanel = createButtons(project, model,
                                     busyStateModel, dataProvider, securityService, stateService).apply {
      border = JBUI.Borders.empty(UIUtil.DEFAULT_VGAP, 0)
    }

    Controller(model, securityService, stateLabel, accessDeniedLabel)

    return NonOpaquePanel(VerticalFlowLayout(0, 0)).apply {
      add(stateLabel)
      add(accessDeniedLabel)
      add(buttonsPanel)
    }
  }

  private fun createButtons(project: Project,
                            model: SingleValueModel<out GHPullRequestShort>,
                            busyStateModel: SingleValueModel<Boolean>,
                            dataProvider: GHPRDataProvider,
                            securityService: GHPRSecurityService,
                            stateService: GHPRStateService): JComponent {
    val closeButton = JButton()
    val reopenButton = JButton()

    val mergeButton = JBOptionButton(null, null)

    val errorPanel = HtmlEditorPane().apply {
      foreground = SimpleTextAttributes.ERROR_ATTRIBUTES.fgColor
    }

    ButtonsController(project, model, busyStateModel, dataProvider, securityService, stateService,
                      closeButton, reopenButton, mergeButton, errorPanel)

    return NonOpaquePanel(FlowLayout(FlowLayout.LEADING, 0, 0)).apply {
      add(mergeButton)
      add(closeButton)
      add(reopenButton)
      add(errorPanel)
    }
  }

  private class Controller(private val model: SingleValueModel<out GHPullRequestShort>,
                           private val securityService: GHPRSecurityService,
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

  private class ButtonsController(project: Project,
                                  private val model: SingleValueModel<out GHPullRequestShort>,
                                  private val busyStateModel: SingleValueModel<Boolean>,
                                  dataProvider: GHPRDataProvider,
                                  private val securityService: GHPRSecurityService,
                                  stateService: GHPRStateService,
                                  private val closeButton: JButton,
                                  private val reopenButton: JButton,
                                  private val mergeButton: JBOptionButton,
                                  errorPanel: HtmlEditorPane) {

    val closeAction = GHPRCloseAction(model.value.number, busyStateModel, stateService, errorPanel)
    val reopenAction = GHPRReopenAction(model.value.number, busyStateModel, stateService, errorPanel)

    val mergeAction = GHPRMergeAction(project, model, busyStateModel, stateService, errorPanel)
    val rebaseMergeAction = GHPRRebaseMergeAction(model, busyStateModel, stateService, errorPanel)
    val squashMergeAction = GHPRSquashMergeAction(project, dataProvider, model, busyStateModel, stateService, errorPanel)

    init {
      updateActions()

      model.addValueChangedListener {
        updateActions()
      }
      busyStateModel.addValueChangedListener {
        updateActions()
      }
      closeButton.action = closeAction
      reopenButton.action = reopenAction
    }

    private fun updateActions() {
      val value = model.value
      val canEdit = securityService.currentUserHasPermissionLevel(GHRepositoryPermissionLevel.TRIAGE) ||
                    securityService.currentUser == value.author

      reopenButton.isVisible = canEdit && value.state == GHPullRequestState.CLOSED
      reopenAction.isEnabled = reopenButton.isVisible && !busyStateModel.value

      closeButton.isVisible = canEdit && value.state == GHPullRequestState.OPEN
      closeAction.isEnabled = closeButton.isVisible && !busyStateModel.value

      val canMerge = securityService.currentUserHasPermissionLevel(GHRepositoryPermissionLevel.WRITE)

      mergeButton.isVisible = canMerge && value.state == GHPullRequestState.OPEN && value is GHPullRequest
      val mergeable = mergeButton.isVisible && value is GHPullRequest && value.mergeable == GHPullRequestMergeableState.MERGEABLE &&
                      !busyStateModel.value &&
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