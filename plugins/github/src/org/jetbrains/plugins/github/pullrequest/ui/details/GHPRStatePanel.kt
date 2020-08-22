// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.details

import com.intellij.icons.AllIcons
import com.intellij.ide.plugins.newui.VerticalLayout
import com.intellij.ui.CardLayoutPanel
import com.intellij.ui.components.JBOptionButton
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.ui.components.panels.Wrapper
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UI
import com.intellij.util.ui.UIUtil
import icons.GithubIcons
import net.miginfocom.layout.LC
import net.miginfocom.swing.MigLayout
import org.jetbrains.plugins.github.api.data.GHRepositoryPermissionLevel
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestState
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.data.GHPRMergeabilityState
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRSecurityService
import org.jetbrains.plugins.github.pullrequest.ui.details.action.*
import org.jetbrains.plugins.github.ui.GHHtmlErrorPanel
import org.jetbrains.plugins.github.ui.util.HtmlEditorPane
import org.jetbrains.plugins.github.util.GithubAsyncUtil
import java.awt.FlowLayout
import java.awt.event.ActionEvent
import javax.swing.*

internal class GHPRStatePanel(private val securityService: GHPRSecurityService, private val stateModel: GHPRStateModel)
  : CardLayoutPanel<GHPullRequestState, GHPRStatePanel.StateUI, JComponent>() {

  override fun prepare(key: GHPullRequestState): StateUI {
    return when (key) {
      GHPullRequestState.MERGED -> StateUI.Merged(stateModel)
      GHPullRequestState.CLOSED -> StateUI.Closed(securityService, stateModel)
      GHPullRequestState.OPEN -> StateUI.Open(securityService, stateModel)
    }
  }

  override fun create(ui: StateUI) = ui.createComponent()

  internal sealed class StateUI(protected val stateModel: GHPRStateModel) {

    companion object {
      protected const val STATUSES_GAP = 4
    }

    fun createComponent(): JComponent {
      val statusComponent = createStatusComponent()

      val buttonsPanel = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
        for (button in createButtons()) {
          add(button)
        }
      }
      val errorComponent = HtmlEditorPane().apply {
        foreground = UIUtil.getErrorForeground()
      }
      stateModel.addAndInvokeActionErrorChangedListener {
        errorComponent.setBody(stateModel.actionError?.message.orEmpty())
      }

      val actionsPanel = JPanel(null).apply {
        isOpaque = false
        layout = MigLayout(LC().fill().gridGap("${UI.scale(5)}", "0").insets("0"))

        add(buttonsPanel)
        add(errorComponent)
      }

      return NonOpaquePanel(VerticalLayout(UI.scale(4))).apply {
        border = JBUI.Borders.emptyLeft(4)

        add(statusComponent, VerticalLayout.FILL_HORIZONTAL)
        add(actionsPanel, VerticalLayout.FILL_HORIZONTAL)
      }
    }

    abstract fun createStatusComponent(): JComponent

    abstract fun createButtons(): List<JComponent>

    class Merged(stateModel: GHPRStateModel) : StateUI(stateModel) {

      override fun createStatusComponent() = JLabel(GithubBundle.message("pull.request.state.merged.long"), GithubIcons.PullRequestMerged,
                                                    SwingConstants.LEFT)

      override fun createButtons() = emptyList<JComponent>()
    }

    class Closed(securityService: GHPRSecurityService, stateModel: GHPRStateModel) : StateUI(stateModel) {

      private val canReopen = securityService.currentUserHasPermissionLevel(GHRepositoryPermissionLevel.TRIAGE)
                              || stateModel.viewerDidAuthor

      override fun createStatusComponent(): JComponent {
        val stateLabel = JLabel(GithubBundle.message("pull.request.state.closed.long"), GithubIcons.PullRequestClosed, SwingConstants.LEFT)
        return if (canReopen) stateLabel
        else {
          val accessDeniedLabel = JLabel().apply {
            icon = AllIcons.RunConfigurations.TestError
            text = GithubBundle.message("pull.request.repo.access.required")
          }
          JPanel(VerticalLayout(UI.scale(STATUSES_GAP))).apply {
            add(stateLabel, VerticalLayout.FILL_HORIZONTAL)
            add(accessDeniedLabel, VerticalLayout.FILL_HORIZONTAL)
          }
        }
      }

      override fun createButtons(): List<JComponent> {
        return if (canReopen) {
          val action = GHPRReopenAction(stateModel)
          listOf(JButton(action))
        }
        else emptyList()
      }
    }

    class Open(securityService: GHPRSecurityService, stateModel: GHPRStateModel) : StateUI(stateModel) {

      private val canClose =
        securityService.currentUserHasPermissionLevel(GHRepositoryPermissionLevel.TRIAGE) || stateModel.viewerDidAuthor
      private val canMerge = securityService.currentUserHasPermissionLevel(GHRepositoryPermissionLevel.WRITE)
      private val mergeForbidden = securityService.isMergeForbiddenForProject()
      private val canMarkReadyForReview =
        securityService.currentUserHasPermissionLevel(GHRepositoryPermissionLevel.WRITE) || stateModel.viewerDidAuthor

      private val canCommitMerge = securityService.isMergeAllowed()
      private val canSquashMerge = securityService.isSquashMergeAllowed()
      private val canRebaseMerge = securityService.isRebaseMergeAllowed()

      override fun createStatusComponent(): JComponent {
        val panel = Wrapper()
        LoadingController(panel)
        return panel
      }

      private fun createNotLoadedComponent(isDraft: Boolean): JComponent {
        val stateLabel = JLabel(GithubBundle.message("pull.request.loading.status"), AllIcons.RunConfigurations.TestNotRan,
                                SwingConstants.LEFT)
        val accessDeniedLabel = createAccessDeniedLabel(isDraft)
        return JPanel(VerticalLayout(UI.scale(STATUSES_GAP))).apply {
          add(stateLabel)
          add(accessDeniedLabel)
        }
      }

      private fun createErrorComponent() = GHHtmlErrorPanel.create(GithubBundle.message("pull.request.state.cannot.load"),
                                                                   stateModel.mergeabilityLoadingError!!,
                                                                   object : AbstractAction(GithubBundle.message("retry.action")) {
                                                                     override fun actionPerformed(e: ActionEvent?) {
                                                                       stateModel.reloadMergeabilityState()
                                                                     }
                                                                   }, SwingConstants.LEFT)

      private fun createLoadedComponent(mergeability: GHPRMergeabilityState, isDraft: Boolean): JComponent {
        val statusChecks = GHPRStatusChecksComponent.create(mergeability)

        val conflictsLabel = JLabel().apply {
          when (mergeability.hasConflicts) {
            false -> {
              icon = AllIcons.RunConfigurations.TestPassed
              text = GithubBundle.message("pull.request.conflicts.none")
            }
            true -> {
              icon = AllIcons.RunConfigurations.TestError
              text = GithubBundle.message("pull.request.conflicts.must.be.resolved")
            }
            null -> {
              icon = AllIcons.RunConfigurations.TestNotRan
              text = GithubBundle.message("pull.request.conflicts.checking")
            }
          }
        }

        val requiredReviewsLabel = JLabel().apply {
          val requiredApprovingReviewsCount = mergeability.requiredApprovingReviewsCount
          isVisible = requiredApprovingReviewsCount > 0 && !isDraft

          icon = AllIcons.RunConfigurations.TestError
          text = GithubBundle.message("pull.request.reviewers.required", requiredApprovingReviewsCount)
        }

        val restrictionsLabel = JLabel().apply {
          isVisible = mergeability.isRestricted && !isDraft
          icon = AllIcons.RunConfigurations.TestError
          text = GithubBundle.message("pull.request.not.authorized.to.merge")
        }

        val accessDeniedLabel = createAccessDeniedLabel(isDraft)

        return JPanel(VerticalLayout(UI.scale(STATUSES_GAP))).apply {
          add(statusChecks)
          add(requiredReviewsLabel)
          add(conflictsLabel)
          add(restrictionsLabel)
          add(accessDeniedLabel)
        }
      }

      private fun createAccessDeniedLabel(isDraft: Boolean): JComponent {
        return JLabel().apply {
          when {
            !canClose -> {
              JLabel().apply {
                icon = AllIcons.RunConfigurations.TestError
                text = GithubBundle.message("pull.request.repo.access.required")
              }
            }
            !canMarkReadyForReview && isDraft -> {
              JLabel().apply {
                icon = AllIcons.RunConfigurations.TestError
                text = GithubBundle.message("pull.request.repo.write.access.required")
              }
            }
            !canMerge && !isDraft -> {
              JLabel().apply {
                icon = AllIcons.RunConfigurations.TestError
                text = GithubBundle.message("pull.request.repo.write.access.required")
              }
            }
            mergeForbidden && !isDraft -> {
              JLabel().apply {
                icon = AllIcons.RunConfigurations.TestError
                text = GithubBundle.message("pull.request.merge.disabled")
              }
            }
            else -> {
              isVisible = false
            }
          }
        }
      }

      override fun createButtons(): List<JComponent> {
        val list = mutableListOf<JComponent>()
        if (canMarkReadyForReview) {
          val button = JButton(GHPRMarkReadyForReviewAction(stateModel))
          list.add(button)
          stateModel.addAndInvokeDraftStateListener {
            button.isVisible = stateModel.isDraft
          }
        }

        if (canMerge && !mergeForbidden) {
          val allowedActions = mutableListOf<Action>()
          if (canCommitMerge)
            allowedActions.add(GHPRCommitMergeAction(stateModel))
          if (canRebaseMerge)
            allowedActions.add(GHPRRebaseMergeAction(stateModel))
          if (canSquashMerge)
            allowedActions.add(GHPRSquashMergeAction(stateModel))

          val action = allowedActions.firstOrNull()
          val actions = if (allowedActions.size > 1) Array(allowedActions.size - 1) { allowedActions[it + 1] } else emptyArray()

          val mergeButton = JBOptionButton(action, actions)
          list.add(mergeButton)
          stateModel.addAndInvokeDraftStateListener {
            mergeButton.isVisible = !stateModel.isDraft
          }
        }

        if (canClose) {
          list.add(JButton(GHPRCloseAction(stateModel)))
        }
        return list
      }

      private inner class LoadingController(private val panel: Wrapper) {

        init {
          stateModel.addAndInvokeMergeabilityStateLoadingResultListener(::update)
          stateModel.addAndInvokeDraftStateListener(::update)
        }

        private fun update() {
          val mergeability = stateModel.mergeabilityState
          if (mergeability == null) {
            if (stateModel.mergeabilityLoadingError?.takeIf { !GithubAsyncUtil.isCancellation(it) } == null) {
              panel.setContent(createNotLoadedComponent(stateModel.isDraft))
            }
            else {
              panel.setContent(createErrorComponent())
            }
          }
          else {
            panel.setContent(createLoadedComponent(mergeability, stateModel.isDraft))
            panel.revalidate()
          }
        }
      }
    }
  }
}