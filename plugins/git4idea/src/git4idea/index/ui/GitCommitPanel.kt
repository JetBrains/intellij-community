// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.index.ui

import com.intellij.idea.ActionsBundle
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.CheckboxAction
import com.intellij.openapi.editor.colors.EditorColorsListener
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComponentContainer
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.VcsRoot
import com.intellij.openapi.vcs.ui.CommitMessage
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.panels.HorizontalLayout
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.components.BorderLayoutPanel
import com.intellij.vcs.commit.loadLastCommitMessage
import git4idea.i18n.GitBundle
import java.awt.Component
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.SwingConstants
import javax.swing.border.Border
import javax.swing.border.EmptyBorder

abstract class GitCommitPanel(private val project: Project,
                              parent: Disposable) : BorderLayoutPanel(), EditorColorsListener, ComponentContainer, DataProvider {
  var isAmend: Boolean = false
    internal set(value) {
      if (field != value) {
        field = value
        commitButton.text = getCommitText()
        updateCommitMessage()
      }
    }

  private var lastCommitMessage: String = ""
  private var lastAmendMessage: String = ""

  val commitMessage = CommitMessage(project, false, false, true)
  val commitButton = CommitButton()

  init {
    addToCenter(buildPanel())
    withPreferredHeight(85)

    Disposer.register(this, commitMessage)
    Disposer.register(parent, this)
  }

  private fun buildPanel(): Component {
    val centerPanel = JBUI.Panels.simplePanel()

    val amendActionToolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN,
                                                                             DefaultActionGroup(AmendAction()), true)
    amendActionToolbar.setTargetComponent(this)
    amendActionToolbar.setReservePlaceAutoPopupIcon(false)
    amendActionToolbar.component.isOpaque = false
    amendActionToolbar.component.border = null
    amendActionToolbar.component.background = getButtonPanelBackground()

    val bottomPanel = JBPanel<JBPanel<*>>(HorizontalLayout(JBUI.scale(4), SwingConstants.CENTER))
    bottomPanel.background = getButtonPanelBackground()
    bottomPanel.border = getButtonPanelBorder()
    bottomPanel.add(commitButton)
    bottomPanel.add(amendActionToolbar.component)

    commitMessage.editorField.addSettingsProvider { it.setBorder(JBUI.Borders.emptyLeft(6)) }
    commitMessage.editorField.setPlaceholder(VcsBundle.message("commit.message.placeholder"))

    centerPanel.addToCenter(commitMessage).addToBottom(bottomPanel)
    return centerPanel
  }

  protected abstract fun performCommit()

  protected abstract fun rootsToCommit(): Collection<VcsRoot>

  protected abstract fun isFocused(): Boolean

  private fun getButtonPanelBorder(): Border =
    EmptyBorder(0, JBUI.scale(4), 0, 0)

  private fun getButtonPanelBackground() =
    JBColor { (commitMessage.editorField.editor as? EditorEx)?.backgroundColor ?: UIUtil.getTreeBackground() }

  override fun globalSchemeChange(scheme: EditorColorsScheme?) {
    // todo
  }

  override fun getComponent(): JComponent = this

  override fun getPreferredFocusableComponent(): JComponent = commitMessage.editorField

  override fun getData(dataId: String) = commitMessage.getData(dataId)

  override fun dispose() {
  }

  private fun getCommitText(): String {
    if (isAmend) return ActionsBundle.message("action.Vcs.ToggleAmendCommitMode.text")
    else return GitBundle.getString("commit.action.name")
  }

  private fun updateCommitMessage() {
    if (isAmend) {
      val roots = rootsToCommit()
      if (roots.isEmpty()) return

      val amendMessage = loadLastCommitMessage(project, roots)
      amendMessage?.let {
        lastCommitMessage = commitMessage.text
        lastAmendMessage = it
        commitMessage.text = it
      }
    }
    else {
      if (commitMessage.text == lastAmendMessage) {
        commitMessage.text = lastCommitMessage
      }
    }
  }

  inner class CommitButton : JButton(object : AbstractAction(getCommitText()) {
    override fun actionPerformed(e: ActionEvent) {
      performCommit()
    }
  }) {

    init {
      background = getButtonPanelBackground()
    }

    override fun isDefaultButton(): Boolean = isFocused()
  }

  inner class AmendAction : CheckboxAction(VcsBundle.messagePointer("checkbox.amend")) {
    override fun isSelected(e: AnActionEvent): Boolean = isAmend
    override fun setSelected(e: AnActionEvent, state: Boolean) {
      isAmend = state
    }
  }
}
