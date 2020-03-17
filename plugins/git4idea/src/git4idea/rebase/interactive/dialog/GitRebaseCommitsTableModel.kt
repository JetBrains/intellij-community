// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.rebase.interactive.dialog

import com.intellij.util.ui.EditableModel
import git4idea.rebase.GitRebaseEntryWithDetails
import git4idea.rebase.interactive.GitRebaseTodoModel
import git4idea.rebase.interactive.convertToModel
import javax.swing.table.AbstractTableModel

internal class GitRebaseCommitsTableModel<T : GitRebaseEntryWithDetails>(private val initialEntries: List<T>) : AbstractTableModel(), EditableModel {
  companion object {
    const val COMMIT_ICON_COLUMN = 0
    const val SUBJECT_COLUMN = 1
  }

  var rebaseTodoModel = createRebaseTodoModel()
    private set

  private fun createRebaseTodoModel(): GitRebaseTodoModel<T> = convertToModel(initialEntries)

  fun updateModel(f: (GitRebaseTodoModel<T>) -> Unit) {
    f(rebaseTodoModel)
    fireTableRowsUpdated(0, rowCount)
  }

  fun resetEntries() {
    rebaseTodoModel = createRebaseTodoModel()
    fireTableRowsUpdated(0, rowCount)
  }

  override fun getRowCount() = rebaseTodoModel.elements.size

  override fun getColumnCount() = SUBJECT_COLUMN + 1

  override fun getValueAt(rowIndex: Int, columnIndex: Int): T = getEntry(rowIndex)

  override fun exchangeRows(oldIndex: Int, newIndex: Int) {
    updateModel { rebaseTodoModel ->
      rebaseTodoModel.exchangeIndices(oldIndex, newIndex)
    }
  }

  override fun canExchangeRows(oldIndex: Int, newIndex: Int) = true

  override fun removeRow(idx: Int) {
    throw UnsupportedOperationException()
  }

  override fun addRow() {
    throw UnsupportedOperationException()
  }

  override fun setValueAt(aValue: Any?, rowIndex: Int, columnIndex: Int) {
    if (aValue is String) {
      rebaseTodoModel.reword(rowIndex, aValue)
    }
  }

  fun getEntry(row: Int): T = rebaseTodoModel.elements[row].entry

  override fun isCellEditable(rowIndex: Int, columnIndex: Int) = columnIndex == SUBJECT_COLUMN && rebaseTodoModel.canReword(rowIndex)

  fun getElement(row: Int): GitRebaseTodoModel.Element<T> = rebaseTodoModel.elements[row]

  fun isFirstFixup(child: GitRebaseTodoModel.Element.UniteChild<*>) = child === child.root.children.first()

  fun isLastFixup(child: GitRebaseTodoModel.Element.UniteChild<*>) = child === child.root.children.last()

  fun getCommitMessage(row: Int): String {
    val elementType = getElement(row).type
    return if (elementType is GitRebaseTodoModel.Type.NonUnite.KeepCommit.Reword) {
      elementType.newMessage
    }
    else {
      getEntry(row).commitDetails.fullMessage
    }
  }
}