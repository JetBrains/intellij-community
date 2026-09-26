// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.table

import com.intellij.ui.dualView.TableCellRendererWrapper
import com.intellij.ui.popup.list.SelectablePanel
import com.intellij.ui.popup.list.SelectablePanel.Companion.wrap
import com.intellij.ui.scale.JBUIScale
import com.intellij.vcs.log.ui.table.column.VcsLogColumn
import com.intellij.vcs.log.ui.table.column.VcsLogColumnManager
import org.jetbrains.annotations.ApiStatus
import java.awt.Component
import javax.swing.JComponent
import javax.swing.JTable
import javax.swing.table.TableCellRenderer

@ApiStatus.Internal
internal class VcsLogNewUiTableCellRenderer(
  private val column: VcsLogColumn<*>,
  private val delegate: TableCellRenderer,
  hasMultiplePaths: () -> Boolean,
) : VcsLogNewUiCellWrapper(hasMultiplePaths), TableCellRenderer, VcsLogCellRenderer, TableCellRendererWrapper {
  private var isRight = false
  private var isLeft = false
  private var cachedRenderer: JComponent? = null
  private var hasRootColumn: Boolean = false

  private lateinit var selectablePanel: SelectablePanel

  override fun getComponentToWrap(table: JTable, value: Any, selected: Boolean, hasFocus: Boolean, row: Int, column: Int): JComponent =
    delegate.getTableCellRendererComponent(table, value, selected, hasFocus, row, column) as JComponent

  override fun getTableCellRendererComponent(table: JTable, value: Any, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int): Component =
    getWrappedCellComponent(table, value, isSelected, hasFocus, row, column)

  private fun isRightColumn(column: Int, table: JTable) = column == table.columnCount - 1

  private fun isLeftColumn(column: Int): Boolean {
    // +1 – root column selection is not supported right now
    return column == ROOT_COLUMN_INDEX + 1
  }

  override fun getBaseRenderer(): TableCellRenderer = delegate

  override fun createSelectablePanel(isRightColumn: Boolean, isLeftColumn: Boolean, componentToWrap: JComponent, hasMultiplePaths: Boolean): SelectablePanel {
    if (isRight != isRightColumn || isLeft != isLeftColumn || cachedRenderer !== componentToWrap || hasMultiplePaths != hasRootColumn) {
      cachedRenderer = componentToWrap
      isLeft = isLeftColumn
      isRight = isRightColumn
      hasRootColumn = hasMultiplePaths
      selectablePanel = wrap(createWrappablePanel(componentToWrap, isLeft, isRight))
    }

    return selectablePanel
  }

  override fun getCellController(): VcsLogCellController? {
    if (delegate is VcsLogCellRenderer) {
      return (delegate as VcsLogCellRenderer).getCellController()
    }
    return null
  }

  override fun getPreferredWidth(): VcsLogCellRenderer.PreferredWidth? {
    if (delegate !is VcsLogCellRenderer) return null
    val delegateWidth = delegate.getPreferredWidth() ?: return null

    return when (delegateWidth) {
      is VcsLogCellRenderer.PreferredWidth.Fixed -> {
        VcsLogCellRenderer.PreferredWidth.Fixed { table ->
          val columnModelIndex = VcsLogColumnManager.getInstance().getModelIndex(column)
          val columnViewIndex = table.convertColumnIndexToView(columnModelIndex)
          val preferredWidth = delegateWidth.function(table)
          getAdjustedWidth(table, columnViewIndex, preferredWidth)
        }
      }
      is VcsLogCellRenderer.PreferredWidth.FromData -> {
        VcsLogCellRenderer.PreferredWidth.FromData { table, value, row, column ->
          val width = delegateWidth.function(table, value, row, column) ?: return@FromData null
          getAdjustedWidth(table, column, width)
        }
      }
    }
  }

  private fun getAdjustedWidth(table: JTable, columnViewIndex: Int, width: Int): Int {
    var newWidth = width
    if (isLeftColumn(columnViewIndex)) {
      newWidth += additionalGap
    }
    if (isRightColumn(columnViewIndex, table)) {
      newWidth += additionalGap
    }
    return newWidth
  }

  companion object {
    @JvmStatic
    fun getAdditionalOffset(column: Int): Int {
      if (column == ROOT_COLUMN_INDEX + 1) {
        return JBUIScale.scale(additionalGap)
      }
      return 0
    }

    private const val ROOT_COLUMN_INDEX = 0
  }
}