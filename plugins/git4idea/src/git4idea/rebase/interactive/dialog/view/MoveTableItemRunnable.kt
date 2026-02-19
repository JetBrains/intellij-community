// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.rebase.interactive.dialog.view

import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.ui.AnActionButton
import com.intellij.ui.AnActionButtonRunnable
import com.intellij.ui.TableUtil
import com.intellij.util.ArrayUtil
import git4idea.rebase.GitRebaseEntry
import git4idea.rebase.interactive.dialog.GitRebaseCommitsTableView
import java.util.Arrays

//Copy-paste of com.intellij.ui.TableToolbarDecorator.MoveRunnable with some changes. Added support for selection of items that were moved for more than one row.
internal class MoveTableItemRunnable internal constructor(val delta: Int, val table: GitRebaseCommitsTableView) : AnActionButtonRunnable {
  override fun run(button: AnActionButton) {
    val row: Int = table.editingRow
    val col: Int = table.editingColumn
    val rowCount: Int = table.getModel().rowCount
    TableUtil.stopEditing(table)
    var idx: IntArray = table.selectedRows
    Arrays.sort(idx)
    if (delta > 0) {
      idx = ArrayUtil.reverseArray(idx)
    }

    if (idx.isEmpty()) return
    if (idx[0] + delta < 0) return
    if (idx[idx.size - 1] + delta > rowCount) return
    val elementsToSelect = mutableListOf<GitRebaseEntry>()
    for (i in idx) {
      elementsToSelect.add(table.model.getElement(i).entry)
      table.model.exchangeRows(i, i + delta)
    }
    val indexesToSelect = mutableListOf<Int>()
    for (i in 0 until table.model.rowCount) {
      if (elementsToSelect.contains(table.model.getElement(i).entry)) {
        indexesToSelect.add(i)
      }
    }
    TableUtil.selectRows(table, indexesToSelect.toIntArray())
    TableUtil.scrollSelectionToVisible(table)
    IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown { IdeFocusManager.getGlobalInstance().requestFocus(table, true) }
    if (row != -1 && col != -1) {
      val newEditingRow = row + delta
      if (newEditingRow != -1 && newEditingRow < rowCount) {
        table.editCellAt(newEditingRow, col)
      }
    }
  }
}
