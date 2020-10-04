// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.index.ui

import com.intellij.idea.ActionsBundle
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ex.CheckboxAction
import com.intellij.openapi.editor.colors.EditorColorsListener
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.VcsRoot
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.InclusionListener
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.vcs.commit.NonModalCommitPanel
import com.intellij.vcs.commit.loadLastCommitMessage
import git4idea.i18n.GitBundle
import org.jetbrains.annotations.Nls
import java.awt.Component
import javax.swing.border.Border
import javax.swing.border.EmptyBorder

abstract class GitStageCommitPanel(project: Project, parent: Disposable) : NonModalCommitPanel(project), EditorColorsListener {
  var isAmend: Boolean = false
    internal set(value) {
      if (field != value) {
        field = value
        commitActionsPanel.defaultCommitActionName = getCommitText()
        updateCommitMessage()
      }
    }

  private var lastCommitMessage: String = ""
  private var lastAmendMessage: String = ""

  init {
    addToCenter(buildPanel())
    withPreferredHeight(85)

    Disposer.register(this, commitMessage)
    Disposer.register(parent, this)
  }

  private fun buildPanel(): Component {
    val centerPanel = JBUI.Panels.simplePanel()
    centerPanel.background = getButtonPanelBackground()

    val amendActionToolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN,
                                                                             DefaultActionGroup(AmendAction()), true)
    amendActionToolbar.setTargetComponent(this)
    amendActionToolbar.setReservePlaceAutoPopupIcon(false)
    amendActionToolbar.component.isOpaque = false
    amendActionToolbar.component.border = null

    commitActionsPanel.apply {
      background = getButtonPanelBackground()

      defaultCommitActionName = getCommitText()
      setTargetComponent(this@GitStageCommitPanel)
    }

    val bottomPanel = JBUI.Panels.simplePanel()
    bottomPanel.isOpaque = false
    bottomPanel.border = getButtonPanelBorder()
    bottomPanel.addToLeft(commitActionsPanel)
    bottomPanel.addToRight(amendActionToolbar.component)

    centerPanel.addToCenter(commitMessage).addToBottom(bottomPanel)
    return centerPanel
  }

  protected abstract fun rootsToCommit(): Collection<VcsRoot>

  private fun getButtonPanelBorder(): Border =
    EmptyBorder(0, JBUI.scale(4), 0, 0)

  private fun getButtonPanelBackground() =
    JBColor { (commitMessage.editorField.editor as? EditorEx)?.backgroundColor ?: UIUtil.getTreeBackground() }

  override fun globalSchemeChange(scheme: EditorColorsScheme?) {
    // todo
  }

  override fun activate(): Boolean = true
  override fun refreshData() = Unit

  override fun getDisplayedChanges(): List<Change> = emptyList()
  override fun getIncludedChanges(): List<Change> = emptyList()
  override fun getDisplayedUnversionedFiles(): List<FilePath> = emptyList()
  override fun getIncludedUnversionedFiles(): List<FilePath> = emptyList()

  override fun includeIntoCommit(items: Collection<*>) = Unit
  override fun addInclusionListener(listener: InclusionListener, parent: Disposable) = Unit

  @Nls
  private fun getCommitText(): String {
    if (isAmend) return ActionsBundle.message("action.Vcs.ToggleAmendCommitMode.text")
    else return GitBundle.message("commit.action.name")
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

  inner class AmendAction : CheckboxAction(VcsBundle.messagePointer("checkbox.amend")) {
    override fun isSelected(e: AnActionEvent): Boolean = isAmend
    override fun setSelected(e: AnActionEvent, state: Boolean) {
      isAmend = state
    }
  }
}
