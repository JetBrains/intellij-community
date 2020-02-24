// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.rebase.interactive.dialog

import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.ui.AnActionButton
import com.intellij.ui.ComponentUtil
import com.intellij.ui.TableUtil
import git4idea.i18n.GitBundle
import git4idea.rebase.GitRebaseEntry
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.util.function.Supplier
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.KeyStroke

internal open class ChangeEntryStateSimpleAction(
  protected val action: GitRebaseEntry.Action,
  title: Supplier<String>,
  description: Supplier<String>,
  icon: Icon?,
  protected val table: GitRebaseCommitsTableView
) : AnActionButton(title, description, icon), DumbAware {

  protected open val disableIfAlreadySet = true

  constructor(action: GitRebaseEntry.Action, icon: Icon?, table: GitRebaseCommitsTableView) :
    this(action, action.visibleName, action.visibleName, icon, table)

  init {
    val keyStroke = KeyStroke.getKeyStroke(
      KeyEvent.getExtendedKeyCodeForChar(action.mnemonic),
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
    if (disableIfAlreadySet) {
      val selectedRows = table.selectedRows
      actionIsEnabled(e, selectedRows.any { table.model.getEntryAction(it) != action })
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
  protected val button = object : JButton(action.visibleName.get()) {
    init {
      adjustForToolbar()
      addActionListener {
        val toolbar = ComponentUtil.getParentOfType(ActionToolbar::class.java, this)
        val dataContext = toolbar?.toolbarDataContext ?: DataManager.getInstance().getDataContext(this)
        actionPerformed(
          AnActionEvent.createFromAnAction(this@ChangeEntryStateButtonAction, null, GitInteractiveRebaseDialog.PLACE, dataContext)
        )
      }
      mnemonic = action.mnemonic
    }
  }

  private val buttonPanel = button.withLeftToolbarBorder()

  override fun actionIsEnabled(e: AnActionEvent, isEnabled: Boolean) {
    super.actionIsEnabled(e, isEnabled)
    button.isEnabled = isEnabled
  }

  override fun createCustomComponent(presentation: Presentation, place: String) = buttonPanel
}

internal open class UniteCommitsAction(action: GitRebaseEntry.Action, table: GitRebaseCommitsTableView) :
  ChangeEntryStateSimpleAction(action, null, table) {

  override fun updateButton(e: AnActionEvent) {
    super.updateButton(e)
    val selectedRows = table.selectedRows
    if (selectedRows.size == 1 && table.model.getFixupRootRow(selectedRows.single()) == null) {
      actionIsEnabled(e, false)
    }
  }

  final override fun actionPerformed(e: AnActionEvent) {
    val selectedRows = table.selectedRows
    if (selectedRows.size == 1) {
      val fixupCommitRow = selectedRows.single()
      fixupCommits(table.model.getFixupRootRow(fixupCommitRow)!!, listOf(fixupCommitRow))
    }
    else {
      fixupCommits(selectedRows.first(), selectedRows.drop(1))
    }
  }

  protected open fun fixupCommits(fixupRootRow: Int, commitRows: List<Int>) {
    table.model.keepCommit(fixupRootRow)
    commitRows.forEach { row ->
      table.setValueAt(GitRebaseEntry.Action.FIXUP, row, GitRebaseCommitsTableModel.COMMIT_ICON_COLUMN)
    }
    table.model.moveRowsToFirst(listOf(fixupRootRow) + commitRows)
    TableUtil.selectRows(table, (fixupRootRow..fixupRootRow + commitRows.size).toList().toIntArray())
  }
}

internal class FixupAction(table: GitRebaseCommitsTableView) : UniteCommitsAction(GitRebaseEntry.Action.FIXUP, table)

// squash = reword + fixup
internal class SquashAction(table: GitRebaseCommitsTableView) : UniteCommitsAction(GitRebaseEntry.Action.SQUASH, table) {
  override fun fixupCommits(fixupRootRow: Int, commitRows: List<Int>) {
    super.fixupCommits(fixupRootRow, commitRows)
    val selectedRows = table.selectedRows
    val model = table.model
    model.setValueAt(model.uniteCommitMessages(selectedRows.toList()), fixupRootRow, GitRebaseCommitsTableModel.SUBJECT_COLUMN)
    TableUtil.selectRows(table, intArrayOf(fixupRootRow))
    TableUtil.editCellAt(table, fixupRootRow, GitRebaseCommitsTableModel.SUBJECT_COLUMN)
  }
}

internal class RewordAction(table: GitRebaseCommitsTableView) : ChangeEntryStateButtonAction(GitRebaseEntry.Action.REWORD, table) {
  override val disableIfAlreadySet = false

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