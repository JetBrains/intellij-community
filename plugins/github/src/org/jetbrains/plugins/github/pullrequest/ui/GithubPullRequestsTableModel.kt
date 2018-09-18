// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui

import org.jetbrains.plugins.github.api.data.GithubSearchedIssue
import javax.swing.table.AbstractTableModel

class GithubPullRequestsTableModel : AbstractTableModel() {
  private val items: MutableList<GithubSearchedIssue> = mutableListOf()

  override fun getRowCount() = items.size

  override fun getColumnCount() = 4

  override fun getValueAt(rowIndex: Int, columnIndex: Int) = items[rowIndex]

  fun addItems(newItems: List<GithubSearchedIssue>) {
    val idx = getLastItemIndex()
    items.addAll(newItems)
    fireTableRowsInserted(idx, getLastItemIndex())
  }

  private fun getLastItemIndex() = Math.max(items.size - 1, 0)

  fun clear() {
    items.clear()
    fireTableDataChanged()
  }
}



