// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.table

import com.intellij.ui.hover.TableHoverListener
import com.intellij.ui.popup.list.SelectablePanel
import com.intellij.ui.popup.list.SelectablePanel.Companion.wrap
import com.intellij.ui.popup.list.SelectablePanel.SelectionArcCorners
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.components.BorderLayoutPanel
import org.jetbrains.annotations.ApiStatus
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTable
import javax.swing.table.TableCellRenderer

@ApiStatus.Internal
internal class VcsLogNewUiTableCellRenderer(val delegate: TableCellRenderer) : TableCellRenderer, VcsLogCellRenderer {
  private var isRight = false
  private var isLeft = false
  private var cachedRenderer: JComponent? = null

  private lateinit var selectablePanel: SelectablePanel

  override fun getTableCellRendererComponent(table: JTable,
                                             value: Any,
                                             isSelected: Boolean,
                                             hasFocus: Boolean,
                                             row: Int,
                                             column: Int): Component {
    val columnRenderer = delegate.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column) as JComponent

    // +1 – root column selection is not supported right now
    val isLeftColumn = column == ROOT_COLUMN_INDEX + 1
    val isRightColumn = column == table.columnCount - 1

    updateSelectablePanelIfNeeded(isRightColumn, isLeftColumn, columnRenderer)

    selectablePanel.apply {
      background = getUnselectedBackground(table, row, column, isSelected, hasFocus)
      selectionColor = if (isSelected) VcsLogGraphTable.getSelectionBackground(table.hasFocus()) else null
      selectionArc = 0
      selectionArcCorners = SelectionArcCorners.ALL

      if (isSelected && (isLeft || isRight)) {
        getSelectedRowType(table, row).tune(selectablePanel, isLeft, isRight)
      }
    }

    return selectablePanel
  }



  private fun updateSelectablePanelIfNeeded(isRightColumn: Boolean, isLeftColumn: Boolean, columnRenderer: JComponent) {
    if (isRight != isRightColumn || isLeft != isLeftColumn || cachedRenderer !== columnRenderer) {
      cachedRenderer = columnRenderer
      isLeft = isLeftColumn
      isRight = isRightColumn

      selectablePanel = wrap(createWrappablePanel(columnRenderer, isLeft, isRight))
    }
  }

  private fun createWrappablePanel(renderer: JComponent, isLeft: Boolean = false, isRight: Boolean = false): BorderLayoutPanel {
    val panel = BorderLayoutPanel().addToCenter(renderer).andTransparent()
    if (isLeft) {
      panel.addToLeft(createEmptyPanel())
    }
    if (isRight) {
      panel.addToRight(createEmptyPanel())
    }
    return panel
  }

  override fun getCellController(): VcsLogCellController? {
    if (cachedRenderer != null && cachedRenderer is VcsLogCellRenderer) {
      return (cachedRenderer as VcsLogCellRenderer).cellController
    }
    return null
  }

  private fun getUnselectedBackground(table: JTable, row: Int, column: Int, isSelected: Boolean, hasFocus: Boolean): Color? {
    val hovered = if (isSelected) false else row == TableHoverListener.getHoveredRow(table)
    return (table as VcsLogGraphTable)
      .getStyle(row, column, hasFocus, false, hovered)
      .background
  }

  private fun getSelectedRowType(table: JTable, row: Int): SelectedRowType {
    val selection = table.selectionModel
    val max = selection.maxSelectionIndex
    val min = selection.minSelectionIndex

    if (max == min) return SelectedRowType.SINGLE

    val isTopRowSelected = selection.isSelectedIndex(row - 1)
    val isBottomRowSelected = selection.isSelectedIndex(row + 1)

    return when {
      isTopRowSelected && isBottomRowSelected -> SelectedRowType.MIDDLE
      isTopRowSelected -> SelectedRowType.BOTTOM
      isBottomRowSelected -> SelectedRowType.TOP
      else -> SelectedRowType.SINGLE
    }
  }

  companion object {
    private val INSETS
      get() = 4

    private val ARC
      get() = JBUI.CurrentTheme.Popup.Selection.ARC.get()

    private fun createEmptyPanel(): JPanel = object : JPanel(null) {
      init {
        isOpaque = false
      }

      override fun getPreferredSize(): Dimension = JBDimension(8, 0)
    }

    private const val ROOT_COLUMN_INDEX = 0
  }

  private enum class SelectedRowType {
    SINGLE {
      override fun SelectablePanel.tuneArcAndCorners(isLeft: Boolean, isRight: Boolean) {
        selectionArc = ARC
        selectionArcCorners = when {
          isLeft && isRight -> SelectionArcCorners.ALL
          isLeft -> SelectionArcCorners.LEFT
          isRight -> SelectionArcCorners.RIGHT
          else -> SelectionArcCorners.ALL
        }
      }
    },

    TOP {
      override fun SelectablePanel.tuneArcAndCorners(isLeft: Boolean, isRight: Boolean) {
        selectionArc = ARC
        selectionArcCorners = when {
          isLeft && isRight -> SelectionArcCorners.TOP
          isLeft -> SelectionArcCorners.TOP_LEFT
          isRight -> SelectionArcCorners.TOP_RIGHT
          else -> SelectionArcCorners.ALL
        }
      }
    },

    MIDDLE {
      override fun SelectablePanel.tuneArcAndCorners(isLeft: Boolean, isRight: Boolean) {
        selectionArc = 0
      }
    },

    BOTTOM {
      override fun SelectablePanel.tuneArcAndCorners(isLeft: Boolean, isRight: Boolean) {
        selectionArc = ARC
        selectionArcCorners = when {
          isLeft && isRight -> SelectionArcCorners.BOTTOM
          isLeft -> SelectionArcCorners.BOTTOM_LEFT
          isRight -> SelectionArcCorners.BOTTOM_RIGHT
          else -> SelectionArcCorners.ALL
        }
      }
    };

    fun tune(selectablePanel: SelectablePanel, isLeft: Boolean, isRight: Boolean) {
      selectablePanel.selectionInsets = JBUI.insets(0, if (isLeft) INSETS else 0, 0, if (isRight) INSETS else 0)

      selectablePanel.tuneArcAndCorners(isLeft, isRight)
    }

    protected abstract fun SelectablePanel.tuneArcAndCorners(isLeft: Boolean, isRight: Boolean)
  }
}