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

  private val savedStates = SavedStates(rebaseTodoModel.elements)

  fun updateModel(f: (GitRebaseTodoModel<T>) -> Unit) {
    f(rebaseTodoModel)
    savedStates.addState(rebaseTodoModel.elements)
    fireTableRowsUpdated(0, rowCount)
  }

  fun resetEntries() {
    rebaseTodoModel = createRebaseTodoModel()
    savedStates.addState(rebaseTodoModel.elements)
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
      if (aValue == getEntry(rowIndex).commitDetails.fullMessage) {
        rebaseTodoModel.pick(listOf(rowIndex))
      }
      else {
        rebaseTodoModel.reword(rowIndex, aValue)
      }
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

  fun undo() {
    savedStates.prevState()?.let {
      rebaseTodoModel = GitRebaseTodoModel(it)
    }
    fireTableRowsUpdated(0, rowCount)
  }

  fun redo() {
    savedStates.nextState()?.let {
      rebaseTodoModel = GitRebaseTodoModel(it)
    }
    fireTableRowsUpdated(0, rowCount)
  }

  private class SavedStates<T : GitRebaseEntryWithDetails>(initialState: List<GitRebaseTodoModel.Element<T>>) {
    companion object {
      private const val MAX_SIZE = 10
    }

    private var currentState = 0
    private val states = mutableListOf(copyElements(initialState))

    private fun checkBoundsAndGetState(): List<GitRebaseTodoModel.Element<T>>? {
      if (currentState !in states.indices) {
        currentState = currentState.coerceIn(states.indices)
        return null
      }
      return copyElements(states[currentState])
    }

    fun prevState(): List<GitRebaseTodoModel.Element<T>>? {
      currentState--
      return checkBoundsAndGetState()
    }

    fun nextState(): List<GitRebaseTodoModel.Element<T>>? {
      currentState++
      return checkBoundsAndGetState()
    }

    fun addState(newState: List<GitRebaseTodoModel.Element<T>>) {
      currentState++
      if (currentState == MAX_SIZE) {
        currentState = MAX_SIZE - 1
        states.removeAt(0)
      }
      while (states.lastIndex != currentState - 1) {
        states.removeAt(states.lastIndex)
      }
      states.add(currentState, copyElements(newState))
    }

    private fun copyElements(elements: List<GitRebaseTodoModel.Element<T>>): List<GitRebaseTodoModel.Element<T>> {
      val result = mutableListOf<GitRebaseTodoModel.Element<T>>()
      elements.forEach { elementToCopy ->
        when (elementToCopy) {
          is GitRebaseTodoModel.Element.Simple -> {
            result.add(GitRebaseTodoModel.Element.Simple(elementToCopy.index, elementToCopy.type, elementToCopy.entry))
          }
          is GitRebaseTodoModel.Element.UniteRoot -> {
            result.add(GitRebaseTodoModel.Element.UniteRoot(elementToCopy.index, elementToCopy.type, elementToCopy.entry))
          }
          is GitRebaseTodoModel.Element.UniteChild -> {
            val rootIndex = elementToCopy.root.index
            val root = result[rootIndex] as GitRebaseTodoModel.Element.UniteRoot<T>
            val child = GitRebaseTodoModel.Element.UniteChild(elementToCopy.index, elementToCopy.entry, root)
            root.addChild(child)
            result.add(child)
          }
        }
      }
      return result
    }
  }
}