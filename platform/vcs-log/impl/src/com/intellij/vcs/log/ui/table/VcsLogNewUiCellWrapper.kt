// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.table

import com.intellij.ui.components.JBPanel
import com.intellij.ui.hover.TableHoverListener
import com.intellij.ui.popup.list.SelectablePanel
import com.intellij.ui.popup.list.SelectablePanel.SelectionArcCorners
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.components.BorderLayoutPanel
import org.jetbrains.annotations.ApiStatus
import java.awt.Color
import java.awt.Component
import javax.swing.JComponent
import javax.swing.JTable

@ApiStatus.Internal
abstract class VcsLogNewUiCellWrapper(private val hasMultiplePaths: () -> Boolean) {
  fun getWrappedCellComponent(table: JTable, value: Any, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int): Component {
    val columnRenderer = getComponentToWrap(table, value, isSelected, hasFocus, row, column) as JComponent

    val isHovered = TableHoverListener.getHoveredRow(table) == row

    val isRight = isRightColumn(column, table)
    val isLeft = isLeftColumn(column)
    val hasRootColumn = hasMultiplePaths()
    val selectablePanel = createSelectablePanel(isRight, isLeftColumn(column), columnRenderer, hasRootColumn)
    return selectablePanel.apply {
      background = getUnselectedBackground(table, row, column, hasFocus)
      selectionColor = getSelectionColor(table, row, column, isSelected, hasFocus, isHovered)
      selectionArc = 0
      selectionArcCorners = SelectionArcCorners.ALL

      if (isLeft || isRight) {
        when {
          isSelected -> getSelectedRowType(table, row).tune(selectablePanel, isLeft, isRight, hasRootColumn)
          isHovered -> SelectedRowType.SINGLE.tune(selectablePanel, isLeft, isRight, hasRootColumn)
          else -> {
            selectionArc = ARC // Allow rounded hint
            selectionArcCorners = SelectionArcCorners.NONE
          }
        }
      }
    }
  }

  abstract fun getComponentToWrap(table: JTable, value: Any, selected: Boolean, hasFocus: Boolean, row: Int, column: Int): JComponent

  protected abstract fun createSelectablePanel(
    isRightColumn: Boolean,
    isLeftColumn: Boolean,
    componentToWrap: JComponent,
    hasMultiplePaths: Boolean,
  ): SelectablePanel

  protected fun createWrappablePanel(componentToWrap: JComponent, isLeft: Boolean, isRight: Boolean): BorderLayoutPanel {
    val panel = BorderLayoutPanel().addToCenter(componentToWrap).andTransparent()
    if (isLeft) {
      panel.addToLeft(createEmptyPanel())
    }
    if (isRight) {
      panel.addToRight(createEmptyPanel())
    }
    return panel
  }

  private fun isRightColumn(column: Int, table: JTable) = column == table.columnCount - 1

  private fun isLeftColumn(column: Int): Boolean {
    // +1 – root column selection is not supported right now
    return column == ROOT_COLUMN_INDEX + 1
  }

  private fun getUnselectedBackground(table: JTable, row: Int, column: Int, hasFocus: Boolean): Color? {
    return (table as VcsLogGraphTable)
      .getStyle(row, column, hasFocus, false, false)
      .background
  }

  private fun getSelectionColor(table: JTable, row: Int, column: Int, isSelected: Boolean, hasFocus: Boolean, isHovered: Boolean): Color? {
    return when {
      isSelected -> (table as VcsLogGraphTable).getSelectionBackground(hasFocus, row)

      isHovered -> (table as VcsLogGraphTable)
        .getStyle(row, column, hasFocus, false, true)
        .background

      else -> null
    }
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
    internal val additionalGap
      get() = 8

    private val INSETS
      get() = 4

    private val ARC
      get() = JBUI.CurrentTheme.Popup.Selection.ARC.get()

    private const val ROOT_COLUMN_INDEX = 0

    private fun createEmptyPanel() = JBPanel<JBPanel<*>>(null).andTransparent().withPreferredSize(additionalGap, 0)
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
        selectionArc = ARC // Allow rounded hint
        selectionArcCorners = SelectionArcCorners.NONE
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

    fun tune(selectablePanel: SelectablePanel, isLeft: Boolean, isRight: Boolean, hasRootColumn: Boolean) {
      selectablePanel.selectionInsets = JBUI.insets(0, if (isLeft && !hasRootColumn) INSETS else 0, 0, if (isRight) INSETS else 0)

      selectablePanel.tuneArcAndCorners(isLeft, isRight)
    }

    protected abstract fun SelectablePanel.tuneArcAndCorners(isLeft: Boolean, isRight: Boolean)
  }
}
