// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.rebase.interactive.dialog.view

import com.intellij.codeInsight.hint.HintUtil
import com.intellij.ide.util.PropertiesComponent
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
import git4idea.rebase.GitRebaseEntryWithDetails
import git4idea.rebase.interactive.dialog.GitRebaseCommitsTableView
import org.jetbrains.annotations.NonNls
import java.awt.Component
import java.awt.Cursor
import java.awt.Point
import java.awt.event.ActionEvent
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.util.*
import javax.swing.*
import javax.swing.event.MouseInputAdapter
import javax.swing.table.TableCellEditor
import kotlin.math.max

private fun makeResizable(panel: JPanel, updateHeight: (newHeight: Int) -> Unit) {
  val listener = HeightResizeMouseListener(panel, updateHeight)
  panel.addMouseListener(listener)
  panel.addMouseMotionListener(listener)
}

internal class CommitMessageCellEditor(
  private val project: Project,
  private val table: GitRebaseCommitsTableView,
  private val disposable: Disposable
) : AbstractCellEditor(), TableCellEditor {
  companion object {
    @NonNls
    private const val COMMIT_MESSAGE_HEIGHT_KEY = "Git.Interactive.Rebase.Dialog.Commit.Message.Height"

    private val HINT_HEIGHT = JBUIScale.scale(17)
    private val DEFAULT_COMMIT_MESSAGE_HEIGHT = GitRebaseCommitsTableView.DEFAULT_CELL_HEIGHT * 5

    internal fun canResize(height: Int, point: Point): Boolean = point.y in height - HINT_HEIGHT..height
  }

  private var savedHeight: Int
    get() = PropertiesComponent.getInstance(project).getInt(COMMIT_MESSAGE_HEIGHT_KEY, DEFAULT_COMMIT_MESSAGE_HEIGHT)
    set(value) {
      PropertiesComponent.getInstance(project).setValue(COMMIT_MESSAGE_HEIGHT_KEY, value, DEFAULT_COMMIT_MESSAGE_HEIGHT)
    }

  private val closeEditorAction = object : AbstractAction() {
    override fun actionPerformed(e: ActionEvent?) {
      stopCellEditing()
    }
  }

  /**
   * Used to save edit message history for each commit (e.g. for undo/redo)
   */
  private val commitMessageForEntry = mutableMapOf<GitRebaseEntryWithDetails, CommitMessage>()

  private var lastUsedCommitMessageField: CommitMessage? = null

  private val hint = createHint()

  private fun createCommitMessage() = CommitMessage(project, false, false, true).apply {
    editorField.addSettingsProvider { editor ->
      editor.scrollPane.border = JBUI.Borders.empty()
      registerCloseEditorShortcut(editor, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.CTRL_DOWN_MASK))
      registerCloseEditorShortcut(editor, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.META_DOWN_MASK))
    }
    editorField.setCaretPosition(0)
    Disposer.register(disposable, this)
  }

  private fun registerCloseEditorShortcut(editor: EditorEx, shortcut: KeyStroke) {
    val key = "applyEdit$shortcut"
    editor.contentComponent.inputMap.put(shortcut, key)
    editor.contentComponent.actionMap.put(key, closeEditorAction)
  }

  override fun getTableCellEditorComponent(table: JTable, value: Any?, isSelected: Boolean, row: Int, column: Int): Component {
    val model = this.table.model
    val commitMessageField = commitMessageForEntry.getOrPut(model.getEntry(row)) { createCommitMessage() }
    lastUsedCommitMessageField = commitMessageField
    commitMessageField.text = model.getCommitMessage(row)
    table.setRowHeight(row, savedHeight)
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
      makeResizable(this) { newHeight ->
        val height = max(DEFAULT_COMMIT_MESSAGE_HEIGHT, newHeight)
        table.setRowHeight(row, height)
        savedHeight = height
      }
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

  override fun getCellEditorValue() = lastUsedCommitMessageField?.text ?: ""

  override fun isCellEditable(e: EventObject?) = when {
    table.selectedRowCount > 1 -> false
    e is MouseEvent -> e.clickCount >= 2
    else -> true
  }
}

private class HeightResizeMouseListener(
  private val panel: JPanel,
  private val updateHeight: (newHeight: Int) -> Unit
) : MouseInputAdapter() {
  private var resizedHeight = panel.height
  private var previousPoint: Point? = null

  override fun mouseReleased(e: MouseEvent) {
    updateCursor(e)
    previousPoint = null
  }

  override fun mouseMoved(e: MouseEvent) {
    updateCursor(e)
  }

  override fun mouseDragged(e: MouseEvent) {
    val current = e.locationOnScreen
    previousPoint?.let {
      val deltaY = current.y - it.y
      resizedHeight += deltaY
      updateHeight(resizedHeight)
    }
    previousPoint = current
  }

  override fun mousePressed(e: MouseEvent) {
    previousPoint = e.locationOnScreen
    resizedHeight = panel.height
  }

  private fun updateCursor(e: MouseEvent) {
    val point = e.point
    panel.cursor = if (CommitMessageCellEditor.canResize(panel.height, point)) {
      Cursor.getPredefinedCursor(Cursor.S_RESIZE_CURSOR)
    }
    else {
      Cursor.getDefaultCursor()
    }
  }
}