// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.rebase.interactive.dialog

import com.intellij.ide.DataManager
import com.intellij.ide.ui.laf.darcula.ui.DarculaButtonPainter
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.ui.AnActionButton
import com.intellij.ui.ComponentUtil
import com.intellij.ui.TableUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.components.BorderLayoutPanel
import git4idea.i18n.GitBundle
import git4idea.rebase.GitRebaseEntry
import java.awt.Component
import java.awt.Dimension
import java.awt.Insets
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.KeyStroke

internal open class ChangeEntryStateSimpleAction(
  protected val action: GitRebaseEntry.Action,
  title: String,
  description: String,
  icon: Icon?,
  protected val table: GitRebaseCommitsTableView
) : AnActionButton(title, description, icon), DumbAware {

  constructor(action: GitRebaseEntry.Action, icon: Icon?, table: GitRebaseCommitsTableView) :
    this(action, action.name.capitalize(), action.name.capitalize(), icon, table)

  init {
    val keyStroke = KeyStroke.getKeyStroke(
      KeyEvent.getExtendedKeyCodeForChar(action.mnemonic.toInt()),
      InputEvent.ALT_MASK
    )
    shortcutSet = CustomShortcutSet(KeyboardShortcut(keyStroke, null))
    this.registerCustomShortcutSet(table, null)
  }

  override fun actionPerformed(e: AnActionEvent) {
    table.selectedRows.forEach { row ->
      table.setValueAt(action, row, GitRebaseCommitsTableModel.COMMIT_ICON_COLUMN)
    }
  }

  override fun updateButton(e: AnActionEvent) {
    super.updateButton(e)
    actionIsEnabled(e, true)
    if (table.editingRow != -1 || table.selectedRowCount == 0) {
      actionIsEnabled(e, false)
    }
  }

  protected open fun actionIsEnabled(e: AnActionEvent, isEnabled: Boolean) {
    e.presentation.isEnabled = isEnabled
  }
}

internal open class ChangeEntryStateButtonAction(
  action: GitRebaseEntry.Action,
  table: GitRebaseCommitsTableView
) : ChangeEntryStateSimpleAction(action, null, table), CustomComponentAction, DumbAware {
  companion object {
    private val BUTTON_HEIGHT = JBUI.scale(28)
  }

  protected val button = object : JButton(action.name.capitalize()) {
    init {
      preferredSize = Dimension(preferredSize.width, BUTTON_HEIGHT)
      border = object : DarculaButtonPainter() {
        override fun getBorderInsets(c: Component?): Insets {
          return JBUI.emptyInsets()
        }
      }
      isFocusable = false
      displayedMnemonicIndex = 0
      addActionListener {
        val toolbar = ComponentUtil.getParentOfType(ActionToolbar::class.java, this)
        val dataContext = toolbar?.toolbarDataContext ?: DataManager.getInstance().getDataContext(this)
        actionPerformed(
          AnActionEvent.createFromAnAction(this@ChangeEntryStateButtonAction, null, GitInteractiveRebaseDialog.PLACE, dataContext)
        )
      }
    }
  }

  override fun actionIsEnabled(e: AnActionEvent, isEnabled: Boolean) {
    super.actionIsEnabled(e, isEnabled)
    button.isEnabled = isEnabled
  }

  override fun createCustomComponent(presentation: Presentation, place: String) = BorderLayoutPanel().addToCenter(button).apply {
    border = JBUI.Borders.emptyLeft(6)
  }
}

internal class FixupAction(table: GitRebaseCommitsTableView) : ChangeEntryStateButtonAction(GitRebaseEntry.Action.FIXUP, table) {
  override fun actionPerformed(e: AnActionEvent) {
    val selectedRows = table.selectedRows
    if (selectedRows.size == 1) {
      table.setValueAt(action, selectedRows.first(), GitRebaseCommitsTableModel.COMMIT_ICON_COLUMN)
    }
    else {
      selectedRows.drop(1).forEach { row ->
        table.setValueAt(action, row, GitRebaseCommitsTableModel.COMMIT_ICON_COLUMN)
      }
    }
  }
}

internal class RewordAction(table: GitRebaseCommitsTableView) : ChangeEntryStateButtonAction(GitRebaseEntry.Action.REWORD, table) {
  override fun updateButton(e: AnActionEvent) {
    super.updateButton(e)
    if (table.selectedRowCount != 1) {
      actionIsEnabled(e, false)
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    TableUtil.editCellAt(table, table.selectedRows.single(), GitRebaseCommitsTableModel.SUBJECT_COLUMN)
  }
}

internal class ShowGitRebaseCommandsDialog(private val project: Project, private val table: GitRebaseCommitsTableView) :
  DumbAwareAction(GitBundle.getString("rebase.interactive.dialog.view.git.commands.text")) {

  private fun getEntries(): List<GitRebaseEntry> = table.model.entries.map { it.entry }

  override fun actionPerformed(e: AnActionEvent) {
    val dialog = GitRebaseCommandsDialog(project, getEntries())
    dialog.show()
  }
}