// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui

import com.intellij.ui.ColoredTableCellRenderer
import com.intellij.ui.ScrollingUtil
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.table.JBTable
import com.intellij.util.text.DateFormatUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.jetbrains.plugins.github.api.data.GithubIssueState
import org.jetbrains.plugins.github.api.data.GithubSearchedIssue
import javax.swing.JTable
import javax.swing.ListSelectionModel
import javax.swing.table.DefaultTableColumnModel
import javax.swing.table.TableColumn
import javax.swing.table.TableColumnModel

class GithubPullRequestsTable(model: GithubPullRequestsTableModel) : JBTable(model, createColumnModel()) {

  init {
    setShowVerticalLines(false)
    setShowHorizontalLines(false)
    setShowColumns(false)
    columnSelectionAllowed = false
    intercellSpacing = JBUI.emptySize()
    setTableHeader(InvisibleResizableHeader())
    selectionModel.selectionMode = ListSelectionModel.SINGLE_SELECTION

    ScrollingUtil.installActions(this, false)
    resetDefaultFocusTraversalKeys()
  }

  companion object {
    private fun createColumnModel(): TableColumnModel {
      return DefaultTableColumnModel().apply {
        addColumn(TableColumn(0, 0, createRenderer(true) { it.number.toString() }, null))
        addColumn(TableColumn(1, 0, createRenderer { it.title }, null))
        addColumn(TableColumn(2, 0, createRenderer { it.user.login }, null))
        addColumn(TableColumn(3, 0, createRenderer { DateFormatUtil.formatDateTime(it.createdAt) }, null))
        for (column in columns) {
          column.resizable = true
        }
      }
    }

    private fun createRenderer(strikeOutOnClosed: Boolean = false, textExtractor: (GithubSearchedIssue) -> String) =
      object : PullRequestsTableCellRenderer(strikeOutOnClosed) {
        override fun getTextualValue(pullRequest: GithubSearchedIssue): String = textExtractor(pullRequest)
      }

    private abstract class PullRequestsTableCellRenderer(private val strikeOutOnClosed: Boolean = false) : ColoredTableCellRenderer() {
      override fun customizeCellRenderer(table: JTable, value: Any?, selected: Boolean, hasFocus: Boolean, row: Int, column: Int) {
        value as GithubSearchedIssue
        val textAttributes = if (value.state == GithubIssueState.closed) {
          SimpleTextAttributes(if (strikeOutOnClosed) SimpleTextAttributes.STYLE_STRIKEOUT else SimpleTextAttributes.STYLE_PLAIN,
                               UIUtil.getInactiveTextColor())
        }
        else {
          SimpleTextAttributes.REGULAR_ATTRIBUTES
        }
        append(getTextualValue(value), textAttributes)
        border = null
        background = if (selected)
          if (table.hasFocus()) UIUtil.getListSelectionBackground() else UIUtil.getListUnfocusedSelectionBackground()
        else
          UIUtil.getListBackground()
      }

      protected abstract fun getTextualValue(pullRequest: GithubSearchedIssue): String
    }
  }
}