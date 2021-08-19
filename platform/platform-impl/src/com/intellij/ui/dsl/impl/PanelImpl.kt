// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.dsl.impl

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.OnePixelDivider
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.SeparatorComponent
import com.intellij.ui.TitledSeparator
import com.intellij.ui.components.Label
import com.intellij.ui.dsl.*
import com.intellij.ui.dsl.Row
import com.intellij.ui.dsl.SpacingConfiguration
import com.intellij.ui.dsl.gridLayout.*
import com.intellij.ui.dsl.gridLayout.builders.RowsGridBuilder
import com.intellij.ui.layout.*
import org.jetbrains.annotations.ApiStatus
import javax.swing.*
import javax.swing.text.JTextComponent
import kotlin.math.min

private const val DSL_LABEL_NO_BOTTOM_GAP_PROPERTY = "dsl.label.no.bottom.gap"

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
      TextFieldWithBrowseButton::class,
      TitledSeparator::class
    )
  }

  /**
   * Number of [SpacingConfiguration.horizontalIndent] indents before each row in the panel
   */
  private var panelContext = PanelContext()

  private val rows = mutableListOf<RowImpl>()

  override fun enabled(isEnabled: Boolean): PanelImpl {
    return enabled(isEnabled, rows.indices)
  }

  fun enabled(isEnabled: Boolean, range: IntRange): PanelImpl {
    for (i in range) {
      rows[i].enabled(isEnabled)
    }
    return this
  }

  override fun enabledIf(predicate: ComponentPredicate): PanelImpl {
    enabled(predicate())
    predicate.addListener { enabled(it) }
    return this
  }

  override fun row(label: String, init: Row.() -> Unit): RowImpl {
    return row(Label(label), init)
  }

  override fun row(label: JLabel?, init: Row.() -> Unit): RowImpl {
    val result = RowImpl(dialogPanelConfig, panelContext, label)
    result.init()
    rows.add(result)
    return result
  }

  override fun panel(init: Panel.() -> Unit): PanelImpl {
    val result = PanelImpl(dialogPanelConfig)
    result.init()
    row { }.cell(result)
    return result
  }

  override fun rowsRange(init: Panel.() -> Unit): RowsRangeImpl {
    val result = RowsRangeImpl(this, rows.size)
    this.init()
    result.endIndex = rows.size - 1
    return result
  }

  override fun group(title: String?, init: Panel.() -> Unit): PanelImpl {
    val component = createSeparator(title)
    val result = panel {
      row {
        cell(component)
          .horizontalAlign(HorizontalAlign.FILL)
      }.gap(TopGap.GROUP)
    }
    result.indent(init)
    return result
  }

  override fun groupRowsRange(title: String?, init: Panel.() -> Unit): RowsRangeImpl {
    val result = RowsRangeImpl(this, rows.size)
    val component = createSeparator(title)
    row {
      cell(component)
        .horizontalAlign(HorizontalAlign.FILL)
    }.gap(TopGap.GROUP)
    indent(init)
    result.endIndex = rows.size - 1
    return result
  }

  override fun <T> buttonGroup(binding: PropertyBinding<T>, type: Class<T>, title: String?, init: Panel.() -> Unit) {
    dialogPanelConfig.context.addButtonGroup(BindButtonGroup(binding, type))
    try {
      if (title != null) {
        row {
          label(title)
            .applyToComponent { putClientProperty(DSL_LABEL_NO_BOTTOM_GAP_PROPERTY, true) }
        }.gap(BottomGap.BUTTON_GROUP_HEADER)
      }
      indent(init)
    }
    finally {
      dialogPanelConfig.context.removeLastButtonGroup()
    }
  }

  override fun visible(isVisible: Boolean): PanelImpl {
    return visible(isVisible, rows.indices)
  }

  fun visible(isVisible: Boolean, range: IntRange): PanelImpl {
    for (i in range) {
      rows[i].visible(isVisible)
    }
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

  override fun comment(comment: String?, maxLineLength: Int): PanelImpl {
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

    for (row in rows) {
      if (!checkRow(row)) {
        continue
      }

      rowsGridBuilder.setRowGaps(getRowGaps(row))

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
        val gaps = Gaps(left = row.getIndent(), bottom = dialogPanelConfig.spacing.commentBottomGap)
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

  private fun checkRow(row: RowImpl): Boolean {
    if (row.cells.isEmpty()) {
      LOG.warn("Row should not be empty")
      return false
    }

    return true
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
        val insets = cell.component.origin.insets
        val visualPaddings = Gaps(top = insets.top, left = insets.left, bottom = insets.bottom, right = insets.right)
        val gaps = getComponentGaps(leftGap, rightGap, cell.component)
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

  private fun getComponentGaps(left: Int, right: Int, component: JComponent): Gaps {
    val top = getDefaultVerticalGap(component)
    var bottom = top
    if (component is JLabel && component.getClientProperty(DSL_LABEL_NO_BOTTOM_GAP_PROPERTY) == true) {
      bottom = 0
    }
    return Gaps(top = top, left = left, bottom = bottom, right = right)
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
    val gaps = Gaps(left = leftIndent, bottom = dialogPanelConfig.spacing.commentBottomGap)
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

  private fun getRowGaps(row: RowImpl): RowGaps {
    val top = when (row.topGap) {
      TopGap.GROUP -> dialogPanelConfig.spacing.groupTopGap
      TopGap.SMALL -> dialogPanelConfig.spacing.verticalSmallGap
      null -> 0
    }

    val bottom = when (row.bottomGap) {
      BottomGap.BUTTON_GROUP_HEADER -> dialogPanelConfig.spacing.buttonGroupHeaderBottomGap
      null -> 0
    }

    return if (top > 0 || bottom > 0) RowGaps(top = top, bottom = bottom) else RowGaps.EMPTY
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

internal class PanelContext {
  /**
   * Number of [SpacingConfiguration.horizontalIndent] indents before each row in the panel
   */
  var indentCount: Int = 0
}