// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.rebase.interactive.dialog.view

import com.intellij.codeInsight.hint.HintUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.CommonShortcuts
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.ui.CommitMessage
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.components.BorderLayoutPanel
import git4idea.i18n.GitBundle
import git4idea.rebase.interactive.dialog.GitRebaseCommitsTableView
import java.awt.Component
import java.awt.event.ActionEvent
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.util.*
import javax.swing.*
import javax.swing.table.TableCellEditor

internal class CommitMessageCellEditor(
  project: Project,
  private val table: GitRebaseCommitsTableView,
  disposable: Disposable
) : AbstractCellEditor(), TableCellEditor {
  companion object {
    private val HINT_HEIGHT = JBUIScale.scale(17)
    private val DEFAULT_COMMIT_MESSAGE_HEIGHT = GitRebaseCommitsTableView.DEFAULT_CELL_HEIGHT * 5
  }

  private val closeEditorAction = object : AbstractAction() {
    override fun actionPerformed(e: ActionEvent?) {
      stopCellEditing()
    }
  }

  private val commitMessageField = CommitMessage(project, false, false, true).apply {
    editorField.addSettingsProvider { editor ->
      editor.scrollPane.border = JBUI.Borders.empty()
      registerCloseEditorShortcut(editor, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.CTRL_DOWN_MASK))
      registerCloseEditorShortcut(editor, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.META_DOWN_MASK))
    }
    editorField.setCaretPosition(0)
  }

  private val hint = createHint()

  init {
    Disposer.register(disposable, commitMessageField)
  }

  private fun registerCloseEditorShortcut(editor: EditorEx, shortcut: KeyStroke) {
    val key = "applyEdit$shortcut"
    editor.contentComponent.inputMap.put(shortcut, key)
    editor.contentComponent.actionMap.put(key, closeEditorAction)
  }

  override fun getTableCellEditorComponent(table: JTable, value: Any?, isSelected: Boolean, row: Int, column: Int): Component {
    val model = this.table.model
    commitMessageField.text = model.getCommitMessage(row)
    table.setRowHeight(row, DEFAULT_COMMIT_MESSAGE_HEIGHT)
    val componentPanel = object : BorderLayoutPanel() {
      override fun requestFocus() {
        IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown {
          IdeFocusManager.getGlobalInstance().requestFocus(commitMessageField.editorField, true)
        }
      }
    }
    return componentPanel.addToCenter(commitMessageField).addToBottom(hint).apply {
      background = table.background
      border = JBUI.Borders.merge(IdeBorderFactory.createBorder(), JBUI.Borders.empty(6, 0, 0, 6), true)
    }
  }

  private fun createHint(): JLabel {
    val hint = GitBundle.message("rebase.interactive.dialog.reword.hint.text",
                                 KeymapUtil.getFirstKeyboardShortcutText(CommonShortcuts.CTRL_ENTER))
    val hintLabel = HintUtil.createAdComponent(hint, JBUI.CurrentTheme.BigPopup.advertiserBorder(), SwingConstants.LEFT).apply {
      foreground = JBUI.CurrentTheme.BigPopup.advertiserForeground()
      background = JBUI.CurrentTheme.BigPopup.advertiserBackground()
      isOpaque = true
    }
    val size = hintLabel.preferredSize
    size.height = HINT_HEIGHT
    hintLabel.preferredSize = size
    return hintLabel
  }

  override fun getCellEditorValue() = commitMessageField.text

  override fun isCellEditable(e: EventObject?) = when {
    table.selectedRowCount > 1 -> false
    e is MouseEvent -> e.clickCount >= 2
    else -> true
  }
}