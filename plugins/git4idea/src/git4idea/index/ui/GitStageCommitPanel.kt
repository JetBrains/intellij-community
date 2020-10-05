// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.index.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.editor.colors.EditorColorsListener
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.InclusionListener
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.vcs.commit.NonModalCommitPanel
import java.awt.Component
import javax.swing.border.Border
import javax.swing.border.EmptyBorder

class GitStageCommitPanel(project: Project) : NonModalCommitPanel(project), EditorColorsListener {
  init {
    addToCenter(buildPanel())
    withPreferredHeight(85)

    Disposer.register(this, commitMessage)
  }

  private fun buildPanel(): Component {
    val centerPanel = JBUI.Panels.simplePanel()
    centerPanel.background = getButtonPanelBackground()

    val amendAction = DefaultActionGroup(ActionManager.getInstance().getAction("Vcs.ToggleAmendCommitMode"))
    val amendActionToolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, amendAction, true)
    amendActionToolbar.setTargetComponent(this)
    amendActionToolbar.setReservePlaceAutoPopupIcon(false)
    amendActionToolbar.component.isOpaque = false
    amendActionToolbar.component.border = null

    commitActionsPanel.apply {
      background = getButtonPanelBackground()
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
}
