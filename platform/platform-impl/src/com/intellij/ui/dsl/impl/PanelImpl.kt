// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.dsl.impl

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.OnePixelDivider
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.SeparatorComponent
import com.intellij.ui.TitledSeparator
import com.intellij.ui.components.Label
import com.intellij.ui.dsl.*
import com.intellij.ui.dsl.Row
import com.intellij.ui.dsl.gridLayout.*
import com.intellij.ui.dsl.gridLayout.builders.RowsGridBuilder
import com.intellij.ui.layout.*
import org.jetbrains.annotations.ApiStatus
import javax.swing.*
import javax.swing.text.JTextComponent
import kotlin.math.min

@ApiStatus.Experimental
internal class PanelImpl(private val dialogPanelConfig: DialogPanelConfig) : CellBaseImpl<Panel>(
  dialogPanelConfig), Panel {

  companion object {
    private val LOG = Logger.getInstance(PanelImpl::class.java)

    private val DEFAULT_VERTICAL_GAP_COMPONENTS = setOf(
      AbstractButton::class,
      JComboBox::class,
      JLabel::class,
      JSpinner::class,
      JTextComponent::class,
      TitledSeparator::class
    )
  }

  /**
   * Number of [SpacingConfiguration.horizontalIndent] indents before each row in the panel
   */
  private var panelContext = PanelContext()

  private val rows = mutableListOf<RowImpl>()

  override fun enabled(isEnabled: Boolean): PanelImpl {
    rows.forEach { it.enabled(isEnabled) }
    return this
  }

  override fun row(label: String, init: Row.() -> Unit): Row {
    return row(Label(label), init)
  }

  override fun row(label: JLabel?, init: Row.() -> Unit): RowImpl {
    val result = RowImpl(dialogPanelConfig, panelContext, label)
    result.init()
    rows.add(result)
    return result
  }

  override fun group(title: String?, independent: Boolean, init: Panel.() -> Unit): Row {
    val component = createSeparator(title)
    if (independent) {
      return row {
        val panel = panel {
          row {
            cell(component)
              .horizontalAlign(HorizontalAlign.FILL)
          }
        }

        panel.indent {
          init()
        }

      }.gap(TopGap.GROUP)
    }

    // todo
    return RowImpl(dialogPanelConfig, panelContext)
  }

  override fun <T> buttonGroup(binding: PropertyBinding<T>, type: Class<T>, init: Panel.() -> Unit) {
    dialogPanelConfig.context.addButtonGroup(BindButtonGroup(binding, type))
    try {
      this.init()
    }
    finally {
      dialogPanelConfig.context.removeLastButtonGroup()
    }
  }

  override fun visible(isVisible: Boolean): PanelImpl {
    rows.forEach { it.visible(isVisible) }
    return this
  }

  override fun horizontalAlign(horizontalAlign: HorizontalAlign): PanelImpl {
    super.horizontalAlign(horizontalAlign)
    return this
  }

  override fun verticalAlign(verticalAlign: VerticalAlign): PanelImpl {
    super.verticalAlign(verticalAlign)
    return this
  }

  override fun resizableColumn(): PanelImpl {
    super.resizableColumn()
    return this
  }

  override fun comment(@NlsContexts.DetailedDescription comment: String, maxLineLength: Int): PanelImpl {
    super.comment(comment, maxLineLength)
    return this
  }

  override fun gap(rightGap: RightGap): PanelImpl {
    super.gap(rightGap)
    return this
  }

  fun build(panel: DialogPanel, grid: JBGrid) {
    val maxColumnsCount = getMaxColumnsCount()
    val rowsGridBuilder = RowsGridBuilder(panel, grid = grid)

    for ((index, row) in rows.withIndex()) {
      if (row.cells.isEmpty()) {
        LOG.warn("Row should not be empty")
        continue
      }

      row.topGap?.let {
        if (index > 0) {
          rowsGridBuilder.setRowGaps(RowGaps(top = getRowTopGap(it)))
        }
      }

      when (row.rowLayout) {
        RowLayout.INDEPENDENT -> {
          val subGrid = rowsGridBuilder.subGrid(width = maxColumnsCount, horizontalAlign = HorizontalAlign.FILL,
            verticalAlign = VerticalAlign.FILL, gaps = Gaps(left = row.getIndent()))
          val subGridBuilder = RowsGridBuilder(panel, subGrid)
          val cells = row.cells
          buildRow(cells, row.label != null, 0, cells.size, panel, subGridBuilder)
          subGridBuilder.row()

          buildCommentRow(cells, 0, cells.size, subGridBuilder)
          setLastColumnResizable(subGridBuilder)
          rowsGridBuilder.row()
        }

        RowLayout.LABEL_ALIGNED -> {
          buildCell(row.cells[0], true, row.getIndent(), row.cells.size == 1, 1, panel, rowsGridBuilder)

          if (row.cells.size > 1) {
            val subGrid = rowsGridBuilder.subGrid(width = maxColumnsCount - 1, horizontalAlign = HorizontalAlign.FILL,
              verticalAlign = VerticalAlign.FILL)
            val subGridBuilder = RowsGridBuilder(panel, subGrid)
            val cells = row.cells.subList(1, row.cells.size)
            buildRow(cells, false, 0, cells.size, panel, subGridBuilder)
            setLastColumnResizable(subGridBuilder)
          }
          rowsGridBuilder.row()

          val commentedCellIndex = getCommentedCellIndex(row.cells)
          when {
            commentedCellIndex in 0..1 -> {
              buildCommentRow(row.cells, row.getIndent(), maxColumnsCount, rowsGridBuilder)
            }
            commentedCellIndex > 1 -> {
              // Always put comment for cells with index more than 1 at second cell because it's hard to implement
              // more correct behaviour now. Can be fixed later
              buildCommentRow(listOf(row.cells[0], row.cells[commentedCellIndex]), 0, maxColumnsCount, rowsGridBuilder)
            }
          }
        }

        RowLayout.PARENT_GRID -> {
          buildRow(row.cells, row.label != null, row.getIndent(), maxColumnsCount, panel, rowsGridBuilder)
          rowsGridBuilder.row()

          buildCommentRow(row.cells, row.getIndent(), maxColumnsCount, rowsGridBuilder)
        }
      }

      row.comment?.let {
        val gaps = Gaps(left = row.getIndent(), bottom = dialogPanelConfig.spacing.verticalCommentBottomGap)
        rowsGridBuilder.cell(it, maxColumnsCount, gaps = gaps)
        rowsGridBuilder.row()
      }
    }

    setLastColumnResizable(rowsGridBuilder)
  }

  override fun indent(init: Panel.() -> Unit) {
    panelContext.indentCount++
    try {
      this.init()
    }
    finally {
      panelContext.indentCount--
    }
  }

  private fun setLastColumnResizable(builder: RowsGridBuilder) {
    if (builder.resizableColumns.isEmpty() && builder.columnsCount > 0) {
      builder.resizableColumns = setOf(builder.columnsCount - 1)
    }
  }

  private fun buildRow(cells: List<CellBaseImpl<*>>,
                       firstCellLabel: Boolean,
                       firstCellIndent: Int,
                       maxColumnsCount: Int,
                       panel: DialogPanel,
                       builder: RowsGridBuilder) {
    for ((cellIndex, cell) in cells.withIndex()) {
      val lastCell = cellIndex == cells.size - 1
      val width = if (lastCell) maxColumnsCount - cellIndex else 1
      val leftGap = if (cellIndex == 0) firstCellIndent else 0
      buildCell(cell, firstCellLabel && cellIndex == 0, leftGap, lastCell, width, panel, builder)
    }
  }

  private fun buildCell(cell: CellBaseImpl<*>, rowLabel: Boolean, leftGap: Int, lastCell: Boolean, width: Int,
                        panel: DialogPanel, builder: RowsGridBuilder) {
    val rightGap = getRightGap(cell, lastCell, rowLabel)

    when (cell) {
      is CellImpl<*> -> {
        val insets = cell.component.insets
        val visualPaddings = Gaps(top = insets.top, left = insets.left, bottom = insets.bottom, right = insets.right)
        val verticalGap = getDefaultVerticalGap(cell.component)
        val gaps = Gaps(top = verticalGap, left = leftGap, bottom = verticalGap, right = rightGap)
        builder.cell(cell.component, width = width, horizontalAlign = cell.horizontalAlign, verticalAlign = cell.verticalAlign,
          resizableColumn = cell.resizableColumn,
          gaps = gaps, visualPaddings = visualPaddings)
      }
      is PanelImpl -> {
        // todo visualPaddings
        val gaps = Gaps(left = leftGap, right = rightGap)
        val subGrid = builder.subGrid(width = width, horizontalAlign = cell.horizontalAlign, verticalAlign = cell.verticalAlign,
          gaps = gaps)

        cell.build(panel, subGrid)
      }
    }
  }

  /**
   * Returns default top and bottom gap for [component]
   */
  private fun getDefaultVerticalGap(component: JComponent): Int {
    return if (DEFAULT_VERTICAL_GAP_COMPONENTS.any { clazz ->
        clazz.isInstance(component)
      }) dialogPanelConfig.spacing.verticalComponentGap
    else 0
  }

  private fun getMaxColumnsCount(): Int {
    return rows.maxOf {
      when (it.rowLayout) {
        RowLayout.INDEPENDENT -> 1
        RowLayout.LABEL_ALIGNED -> min(2, it.cells.size)
        RowLayout.PARENT_GRID -> it.cells.size
      }
    }
  }

  private fun getRightGap(cell: CellBaseImpl<*>, lastCell: Boolean, rowLabel: Boolean): Int {
    val rightGap = cell.rightGap
    if (lastCell) {
      if (rightGap != null) {
        LOG.warn("Right gap is set for last cell and will be ignored: rightGap = $rightGap")
      }
      return 0
    }

    if (rightGap != null) {
      return when (rightGap) {
        RightGap.SMALL -> dialogPanelConfig.spacing.horizontalSmallGap
      }
    }

    return if (rowLabel) dialogPanelConfig.spacing.horizontalSmallGap else dialogPanelConfig.spacing.horizontalDefaultGap
  }


  /**
   * Appends comment (currently one comment for a row is supported, can be fixed later)
   */
  private fun buildCommentRow(cells: List<CellBaseImpl<*>>,
                              firstCellIndent: Int,
                              maxColumnsCount: Int,
                              builder: RowsGridBuilder) {
    val commentedCellIndex = getCommentedCellIndex(cells)
    if (commentedCellIndex < 0) {
      return
    }

    val cell = cells[commentedCellIndex]
    val leftIndent = getAdditionalHorizontalIndent(cell) +
                     if (commentedCellIndex == 0) firstCellIndent else 0
    val gaps = Gaps(left = leftIndent, bottom = dialogPanelConfig.spacing.verticalCommentBottomGap)
    builder.skip(commentedCellIndex)
    builder.cell(cell.comment!!, maxColumnsCount - commentedCellIndex, gaps = gaps)
    builder.row()
    return
  }

  private fun getAdditionalHorizontalIndent(cell: CellBaseImpl<*>): Int {
    return if (cell is CellImpl<*> && cell.viewComponent is JToggleButton)
      dialogPanelConfig.spacing.horizontalToggleButtonIndent
    else
      0
  }

  private fun getCommentedCellIndex(cells: List<CellBaseImpl<*>>): Int {
    return cells.indexOfFirst { it.comment != null }
  }

  private fun getRowTopGap(topGap: TopGap): Int {
    return when (topGap) {
      TopGap.GROUP -> dialogPanelConfig.spacing.verticalGroupTopGap
    }
  }

  private fun createSeparator(@NlsContexts.BorderTitle title: String?): JComponent {
    if (title == null) {
      return SeparatorComponent(0, OnePixelDivider.BACKGROUND, null)
    }

    val result = TitledSeparator(title)
    result.border = null
    return result
  }
}

internal class PanelContext(
  /**
   * Number of [SpacingConfiguration.horizontalIndent] indents before each row in the panel
   */
  var indentCount: Int = 0)