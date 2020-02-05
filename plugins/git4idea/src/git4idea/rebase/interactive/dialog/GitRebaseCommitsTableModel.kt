// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.rebase.interactive.dialog

import com.intellij.util.ui.EditableModel
import git4idea.rebase.GitRebaseEntry
import git4idea.rebase.GitRebaseEntryWithDetails
import javax.swing.table.AbstractTableModel
import kotlin.math.max
import kotlin.math.min

internal class GitRebaseCommitsTableModel(initialEntries: List<GitRebaseEntryWithEditedMessage>) : AbstractTableModel(), EditableModel {
  companion object {
    const val COMMIT_ICON_COLUMN = 0
    const val SUBJECT_COLUMN = 1
  }

  private val rows: MutableList<CommitTableModelRow> = initialEntries.mapIndexed { i, entry ->
    CommitTableModelRow(i, entry)
  }.toMutableList()

  val entries: List<GitRebaseEntryWithEditedMessage>
    get() = rows.map { it.entry }

  fun resetEntries() {
    rows.sortBy { it.initialIndex }
    rows.forEach {
      it.action = it.initialAction
      it.newMessage = it.entry.entry.commitDetails.fullMessage
    }
    fireTableRowsUpdated(0, rows.size - 1)
  }

  override fun getRowCount() = rows.size

  override fun getColumnCount() = SUBJECT_COLUMN + 1

  override fun getValueAt(rowIndex: Int, columnIndex: Int): Any = when (columnIndex) {
    COMMIT_ICON_COLUMN -> rows[rowIndex]
    SUBJECT_COLUMN -> rows[rowIndex].newMessage
    else -> throw IllegalArgumentException("Unsupported column index: $columnIndex")
  }

  override fun exchangeRows(oldIndex: Int, newIndex: Int) {
    val movingElement = rows.removeAt(oldIndex)
    rows.add(newIndex, movingElement)
    fireTableRowsUpdated(min(oldIndex, newIndex), max(oldIndex, newIndex))
  }

  override fun canExchangeRows(oldIndex: Int, newIndex: Int) = true

  override fun removeRow(idx: Int) {
    throw UnsupportedOperationException()
  }

  override fun addRow() {
    throw UnsupportedOperationException()
  }

  override fun setValueAt(aValue: Any?, rowIndex: Int, columnIndex: Int) {
    when (aValue) {
      is GitRebaseEntry.Action -> {
        val row = rows[rowIndex]
        if (aValue != GitRebaseEntry.Action.REWORD) {
          row.newMessage = row.details.fullMessage
        }
        row.action = aValue
      }
      is String -> {
        rows[rowIndex].action =
          if (rows[rowIndex].details.fullMessage != aValue) {
            GitRebaseEntry.Action.REWORD
          }
          else {
            GitRebaseEntry.Action.PICK
          }
        rows[rowIndex].newMessage = aValue
      }
      else -> throw IllegalArgumentException()
    }
    fireTableRowsUpdated(rowIndex, rowIndex)
  }

  override fun isCellEditable(rowIndex: Int, columnIndex: Int) = true

  fun getEntry(row: Int): GitRebaseEntryWithEditedMessage = rows[row].entry

  fun getEntryAction(row: Int): GitRebaseEntry.Action = rows[row].entry.entry.action

  fun isFixupOrDrop(row: Int): Boolean {
    val entryAction = getEntryAction(row)
    return entryAction == GitRebaseEntry.Action.FIXUP || entryAction == GitRebaseEntry.Action.DROP
  }

  fun isFixupRoot(rowToCheck: Int): Boolean {
    if (isFixupOrDrop(rowToCheck)) {
      return false
    }
    for (row in rowToCheck + 1 until rowCount) {
      val rowAction = getEntryAction(row)
      if (rowAction != GitRebaseEntry.Action.DROP) {
        return rowAction == GitRebaseEntry.Action.FIXUP
      }
    }
    return false
  }

  fun isFirstFixup(rowToCheck: Int): Boolean {
    if (getEntryAction(rowToCheck) != GitRebaseEntry.Action.FIXUP) {
      return false
    }
    for (row in rowToCheck - 1 downTo 0) {
      val rowAction = getEntryAction(row)
      if (rowAction != GitRebaseEntry.Action.DROP) {
        return rowAction != GitRebaseEntry.Action.FIXUP
      }
    }
    return true
  }

  fun isLastFixup(rowToCheck: Int): Boolean {
    if (getEntryAction(rowToCheck) != GitRebaseEntry.Action.FIXUP) {
      return false
    }
    for (row in rowToCheck + 1 until rowCount) {
      val rowAction = getEntryAction(row)
      if (rowAction != GitRebaseEntry.Action.DROP) {
        return rowAction != GitRebaseEntry.Action.FIXUP
      }
    }
    return true
  }

  private class CommitTableModelRow(val initialIndex: Int, val entry: GitRebaseEntryWithEditedMessage) {
    val initialAction = entry.entry.action
    val details = entry.entry.commitDetails
    var action
      get() = entry.entry.action
      set(value) {
        entry.entry.action = value
      }
    var newMessage
      get() = entry.newMessage
      set(value) {
        entry.newMessage = value
      }
  }
}

internal class GitRebaseEntryWithEditedMessage(
  val entry: GitRebaseEntryWithDetails,
  var newMessage: String = entry.commitDetails.fullMessage
)