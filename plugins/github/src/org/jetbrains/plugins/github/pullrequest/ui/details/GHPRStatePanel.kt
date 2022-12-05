// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.details

import com.intellij.ui.CardLayoutPanel
import com.intellij.ui.components.JBOptionButton
import com.intellij.util.ui.NamedColorUtil
import net.miginfocom.layout.CC
import net.miginfocom.layout.LC
import net.miginfocom.swing.MigLayout
import org.jetbrains.plugins.github.api.data.GHRepositoryPermissionLevel
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestState
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRSecurityService
import org.jetbrains.plugins.github.pullrequest.ui.details.action.*
import org.jetbrains.plugins.github.pullrequest.ui.details.model.GHPRStateModel
import org.jetbrains.plugins.github.ui.util.HtmlEditorPane
import java.awt.FlowLayout
import javax.swing.Action
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel

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
    fun createComponent(): JComponent {
      val buttonsPanel = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
        isOpaque = false
        for (button in createButtons()) {
          add(button)
        }
      }
      val errorComponent = HtmlEditorPane().apply {
        foreground = NamedColorUtil.getErrorForeground()
      }
      stateModel.addAndInvokeActionErrorChangedListener {
        errorComponent.setBody(stateModel.actionError?.message.orEmpty())
      }

      return JPanel(null).apply {
        isOpaque = false
        layout = MigLayout(LC().fill().flowY().gridGap("5", "0").insets("0"))

        add(buttonsPanel)
        add(errorComponent, CC().minWidth("0"))
      }
    }

    abstract fun createButtons(): List<JComponent>

    class Merged(stateModel: GHPRStateModel) : StateUI(stateModel) {

      override fun createButtons() = emptyList<JComponent>()
    }

    class Closed(securityService: GHPRSecurityService, stateModel: GHPRStateModel) : StateUI(stateModel) {

      private val canReopen = securityService.currentUserHasPermissionLevel(GHRepositoryPermissionLevel.TRIAGE)
                              || stateModel.viewerDidAuthor

      override fun createButtons(): List<JComponent> {
        return if (canReopen) {
          val action = GHPRReopenAction(stateModel)
          listOf(JButton(action).apply {
            isOpaque = false
          })
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

        return list.onEach { component ->
          component.isOpaque = false
        }
      }
    }
  }
}