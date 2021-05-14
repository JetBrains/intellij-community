// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit

import com.intellij.codeInsight.hint.HintUtil.PROMOTION_PANE_KEY
import com.intellij.icons.AllIcons
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.IdeActions.ACTION_CHECKIN_PROJECT
import com.intellij.openapi.actionSystem.ex.ActionUtil.invokeAction
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.IconButton
import com.intellij.openapi.vcs.VcsBundle.message
import com.intellij.openapi.vcs.changes.ui.DefaultCommitChangeListDialog
import com.intellij.openapi.vcs.changes.ui.SwitchToCommitDialogHint
import com.intellij.ui.HyperlinkLabel
import com.intellij.ui.IdeBorderFactory.createBorder
import com.intellij.ui.InplaceButton
import com.intellij.ui.JBColor
import com.intellij.ui.SideBorder
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.panels.HorizontalLayout
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.util.ui.JBUI.Borders.empty
import com.intellij.util.ui.JBUI.Borders.merge
import com.intellij.util.ui.JBUI.scale
import com.intellij.util.ui.components.BorderLayoutPanel
import com.intellij.vcs.commit.CommitModeManager.Companion.setCommitFromLocalChanges
import com.intellij.vcs.commit.NonModalCommitPromoter.Companion.getPromotionState
import com.intellij.vcs.commit.NonModalCommitPromotionState.*
import com.intellij.vcs.commit.NonModalCommitUsagesCollector.logPromotionEvent
import java.awt.Color
import javax.swing.JComponent

private const val PROMOTION_STATE_KEY = "NonModalCommitPromotionState"
private fun isDontShowAgain(): Boolean = getPromotionState().let { it == ACCEPTED || it == REJECTED }
private fun setPromotionState(project: Project, value: NonModalCommitPromotionState) {
  PropertiesComponent.getInstance().setValue(PROMOTION_STATE_KEY, value.name)
  logPromotionEvent(project, value)
}

internal enum class NonModalCommitPromotionState {
  SHOWN,
  ACCEPTED,
  REJECTED
}

@Service
internal class NonModalCommitPromoter(private val project: Project) {
  private val commitModeManager: CommitModeManager get() = CommitModeManager.getInstance(project)

  fun getPromotionPanel(commitDialog: DefaultCommitChangeListDialog): JComponent? {
    if (!commitDialog.isDefaultCommitEnabled) return null
    if (isDontShowAgain()) return null
    if (commitModeManager.run { !canSetNonModal() || getCurrentCommitMode() != CommitMode.ModalCommitMode }) return null

    setPromotionState(project, SHOWN)
    return NonModalCommitPromotionPanel(commitDialog)
  }

  companion object {
    fun getInstance(project: Project): NonModalCommitPromoter = project.service()

    fun getPromotionState(): NonModalCommitPromotionState? {
      val value = PropertiesComponent.getInstance().getValue(PROMOTION_STATE_KEY) ?: return null

      return try {
        enumValueOf<NonModalCommitPromotionState>(value)
      }
      catch (e: IllegalArgumentException) {
        null
      }
    }
  }
}

private class NonModalCommitPromotionPanel(private val commitDialog: DefaultCommitChangeListDialog) : BorderLayoutPanel() {
  init {
    border = merge(empty(10), createBorder(JBColor.border(), SideBorder.BOTTOM), true)

    addToCenter(JBLabel().apply {
      icon = AllIcons.Ide.Gift
      text = message("non.modal.commit.promoter.text")
    })
    addToRight(NonOpaquePanel(HorizontalLayout(12)).apply {
      add(createSwitchAction())
      add(createCloseAction())
    })
  }

  override fun getBackground(): Color? =
    EditorColorsManager.getInstance().globalScheme.getColor(PROMOTION_PANE_KEY) ?: super.getBackground()

  private fun createSwitchAction(): JComponent =
    HyperlinkLabel(message("non.modal.commit.promoter.use.non.modal.action.text")).apply {
      addHyperlinkListener {
        setPromotionState(commitDialog.project, ACCEPTED)
        commitDialog.doCancelAction()

        setCommitFromLocalChanges(commitDialog.project, true)
        SwitchToCommitDialogHint.install(commitDialog.project)

        val commitAction = ActionManager.getInstance().getAction(ACTION_CHECKIN_PROJECT) ?: return@addHyperlinkListener
        invokeAction(commitAction, SimpleDataContext.getProjectContext(commitDialog.project), ActionPlaces.UNKNOWN, null, null)
      }
    }

  private fun createCloseAction(): JComponent =
    InplaceButton(
      IconButton(
        message("non.modal.commit.promoter.dont.show.again.action.text"),
        AllIcons.Actions.Close,
        AllIcons.Actions.CloseHovered
      )
    ) {
      setPromotionState(commitDialog.project, REJECTED)
      this@NonModalCommitPromotionPanel.isVisible = false
    }
}