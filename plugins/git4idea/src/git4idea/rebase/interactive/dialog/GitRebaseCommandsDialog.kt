// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.rebase.interactive.dialog

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.components.BorderLayoutPanel
import git4idea.i18n.GitBundle
import git4idea.rebase.GitRebaseEntry
import git4idea.rebase.interactive.dialog.GitRebaseCommandsTableModel.Companion.ACTION_COLUMN
import git4idea.rebase.interactive.dialog.GitRebaseCommandsTableModel.Companion.HASH_COLUMN
import javax.swing.JComponent
import javax.swing.table.AbstractTableModel
import javax.swing.table.TableModel

internal class GitRebaseCommandsDialog(
  project: Project,
  entries: List<GitRebaseEntry>
) : DialogWrapper(project, false) {
  private val table = GitRebaseCommandsTable(entries)

  init {
    title = GitBundle.message("rebase.interactive.dialog.git.commands")
    init()
  }

  override fun createCenterPanel(): JComponent = BorderLayoutPanel()
    .addToCenter(JBScrollPane(table))
    .withPreferredSize(600, 400)

  override fun createActions() = arrayOf(okAction)

  override fun getDimensionServiceKey() = "Git.Interactive.Rebase.Editor.Like.Entries.Dialog"
}

private class GitRebaseCommandsTable(entries: List<GitRebaseEntry>) : JBTable(GitRebaseCommandsTableModel(entries)) {
  init {
    adjustColumnWidth(ACTION_COLUMN)
    adjustColumnWidth(HASH_COLUMN)
  }

  private fun adjustColumnWidth(columnIndex: Int) {
    val contentWidth = getExpandedColumnWidth(columnIndex) + UIUtil.DEFAULT_HGAP
    val column = columnModel.getColumn(columnIndex)
    column.maxWidth = contentWidth
    column.preferredWidth = contentWidth
  }
}

private class GitRebaseCommandsTableModel(private val entries: List<GitRebaseEntry>) : AbstractTableModel(), TableModel {
  companion object {
    const val ACTION_COLUMN = 0
    const val HASH_COLUMN = 1
    const val SUBJECT_COLUMN = 2
  }

  override fun getColumnName(column: Int) = when (column) {
    ACTION_COLUMN -> GitBundle.message("rebase.interactive.dialog.git.commands.column.action")
    HASH_COLUMN -> GitBundle.message("rebase.interactive.dialog.git.commands.column.hash")
    SUBJECT_COLUMN -> GitBundle.message("rebase.interactive.dialog.git.commands.column.subject")
    else -> throw IllegalArgumentException("Unsupported column index: $column")
  }

  override fun getRowCount() = entries.size

  override fun getColumnCount() = 3

  override fun getValueAt(rowIndex: Int, columnIndex: Int) = when (columnIndex) {
    ACTION_COLUMN -> entries[rowIndex].action.toString()
    HASH_COLUMN -> entries[rowIndex].commit
    SUBJECT_COLUMN -> entries[rowIndex].subject
    else -> throw IllegalArgumentException("Unsupported column index: $columnIndex")
  }
}