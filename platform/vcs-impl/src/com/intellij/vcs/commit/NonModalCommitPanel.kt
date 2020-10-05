// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.editor.colors.EditorColorsListener
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComponentContainer
import com.intellij.openapi.vcs.VcsBundle.message
import com.intellij.openapi.vcs.checkin.CheckinHandler
import com.intellij.openapi.vcs.ui.CommitMessage
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.util.ui.JBUI.Borders.emptyLeft
import com.intellij.util.ui.JBUI.Panels.simplePanel
import com.intellij.util.ui.JBUI.scale
import com.intellij.util.ui.UIUtil.getTreeBackground
import com.intellij.util.ui.components.BorderLayoutPanel
import java.awt.LayoutManager
import javax.swing.JComponent
import javax.swing.border.Border
import javax.swing.border.EmptyBorder

private fun panel(layout: LayoutManager): JBPanel<*> = JBPanel<JBPanel<*>>(layout)

abstract class NonModalCommitPanel(
  val project: Project,
  val commitActionsPanel: CommitActionsPanel = CommitActionsPanel()
) : BorderLayoutPanel(),
    NonModalCommitWorkflowUi,
    CommitActionsUi by commitActionsPanel,
    ComponentContainer,
    EditorColorsListener {

  private val dataProviders = mutableListOf<DataProvider>()

  protected val centerPanel = simplePanel()
  protected var bottomPanel: JBPanel<*>.() -> Unit = { }

  private val actions = ActionManager.getInstance().getAction("ChangesView.CommitToolbar") as ActionGroup
  val toolbar = ActionManager.getInstance().createActionToolbar(COMMIT_TOOLBAR_PLACE, actions, true).apply {
    setTargetComponent(this@NonModalCommitPanel)
    component.isOpaque = false
  }

  val commitMessage = CommitMessage(project, false, false, true).apply {
    editorField.addSettingsProvider { it.setBorder(emptyLeft(6)) }
    editorField.setPlaceholder(message("commit.message.placeholder"))
  }

  override val commitMessageUi: CommitMessageUi get() = commitMessage

  override fun getComponent(): JComponent = this
  override fun getPreferredFocusableComponent(): JComponent = commitMessage.editorField

  override fun getData(dataId: String) = getDataFromProviders(dataId) ?: commitMessage.getData(dataId)
  fun getDataFromProviders(dataId: String) = dataProviders.asSequence().mapNotNull { it.getData(dataId) }.firstOrNull()

  override fun addDataProvider(provider: DataProvider) {
    dataProviders += provider
  }

  override fun startBeforeCommitChecks() = Unit
  override fun endBeforeCommitChecks(result: CheckinHandler.ReturnResult) = Unit

  override fun dispose() = Unit

  override fun globalSchemeChange(scheme: EditorColorsScheme?) {
    commitActionsPanel.border = getButtonPanelBorder()
  }

  protected fun buildLayout() {
    commitActionsPanel.apply {
      border = getButtonPanelBorder()
      background = getButtonPanelBackground()

      setTargetComponent(this@NonModalCommitPanel)
    }
    centerPanel
      .addToCenter(commitMessage)
      .addToBottom(panel(VerticalLayout(0)).apply {
        background = getButtonPanelBackground()
        bottomPanel()
      })

    addToCenter(centerPanel)
    withPreferredHeight(85)
  }

  private fun getButtonPanelBorder(): Border =
    EmptyBorder(0, scale(3), (scale(6) - commitActionsPanel.getBottomInset()).coerceAtLeast(0), 0)

  private fun getButtonPanelBackground() =
    JBColor { (commitMessage.editorField.editor as? EditorEx)?.backgroundColor ?: getTreeBackground() }

  companion object {
    internal const val COMMIT_TOOLBAR_PLACE: String = "ChangesView.CommitToolbar"
  }
}