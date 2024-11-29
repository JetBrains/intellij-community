// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.dsl.builder.impl

import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.SeparatorComponent
import com.intellij.ui.TitledSeparator
import com.intellij.ui.components.JBLabel
import com.intellij.ui.dsl.UiDslException
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.dsl.gridLayout.*
import com.intellij.ui.layout.ComponentPredicate
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.ApiStatus
import java.awt.Color
import javax.swing.JComponent
import javax.swing.JLabel

@ApiStatus.Internal
internal class PanelImpl(private val dialogPanelConfig: DialogPanelConfig,
                         var spacingConfiguration: SpacingConfiguration,
                         private val parent: RowImpl?) : CellBaseImpl<Panel>(), Panel {

  val rows: List<RowImpl>
    get() = _rows

  private var panelContext = PanelContext()

  private val _rows = mutableListOf<RowImpl>()
  private val rowsRanges = mutableListOf<RowsRangeImpl>()

  private var visible = true
  private var enabled = true

  override fun row(label: String, init: Row.() -> Unit): RowImpl {
    if (label.isEmpty()) {
      val result = RowImpl(dialogPanelConfig, panelContext, this, RowLayout.LABEL_ALIGNED)
      result.cell()
      result.init()
      _rows.add(result)
      return result
    }
    else {
      return row(createLabel(label), init)
    }
  }

  override fun row(label: JLabel?, init: Row.() -> Unit): RowImpl {
    val result: RowImpl
    if (label == null) {
      result = RowImpl(dialogPanelConfig, panelContext, this, RowLayout.INDEPENDENT)
    } else {
      label.putClientProperty(DslComponentProperty.ROW_LABEL, true)
      result = RowImpl(dialogPanelConfig, panelContext, this, RowLayout.LABEL_ALIGNED)
      result.cell(label)
    }
    result.init()
    _rows.add(result)

    if (label != null && result.cells.size > 1) {
      labelCell(label, result.cells[1])
    }

    return result
  }

  override fun twoColumnsRow(column1: (Row.() -> Unit)?, column2: (Row.() -> Unit)?): Row {
    if (column1 == null && column2 == null) {
      throw UiDslException("Both columns cannot be null")
    }

    return row {
      panel {
        row {
          if (column1 == null) {
            cell()
          }
          else {
            column1()
          }
        }
      }.align(AlignY.TOP).gap(RightGap.COLUMNS)
      panel {
        row {
          if (column2 == null) {
            cell()
          }
          else {
            column2()
          }
        }
      }.align(AlignY.TOP)
    }.layout(RowLayout.PARENT_GRID)
  }

  override fun threeColumnsRow(column1: (Row.() -> Unit)?, column2: (Row.() -> Unit)?, column3: (Row.() -> Unit)?): Row {
    if (column1 == null && column2 == null && column3 == null) {
      throw UiDslException("All columns cannot be null")
    }

    return row {
      panel {
        row {
          if (column1 == null) {
            cell()
          }
          else {
            column1()
          }
        }
      }.gap(RightGap.COLUMNS)
      panel {
        row {
          if (column2 == null) {
            cell()
          }
          else {
            column2()
          }
        }
      }.gap(RightGap.COLUMNS)
      panel {
        row {
          if (column3 == null) {
            cell()
          }
          else {
            column3()
          }
        }
      }
    }.layout(RowLayout.PARENT_GRID)
  }

  override fun separator(background: Color?): Row {
    return createSeparatorRow(null, background)
  }

  override fun panel(init: Panel.() -> Unit): PanelImpl {
    lateinit var result: PanelImpl
    row {
      result = panel(init).align(AlignY.FILL) as PanelImpl
    }
    return result
  }

  override fun rowsRange(init: Panel.() -> Unit): RowsRangeImpl {
    val result = createRowRange()
    this.init()
    result.endIndex = _rows.size - 1
    return result
  }

  override fun group(title: String?, indent: Boolean, init: Panel.() -> Unit): RowImpl {
    return groupImpl(toSeparatorLabel(title), indent, init)
  }

  override fun group(title: JBLabel, indent: Boolean, init: Panel.() -> Unit): RowImpl {
    return groupImpl(title, indent, init)
  }

  private fun groupImpl(title: JBLabel?, indent: Boolean, init: Panel.() -> Unit): RowImpl {
    val result = row {
      panel {
        createSeparatorRow(title)
        if (indent) {
          indent(init)
        }
        else {
          init()
        }
      }.align(AlignY.FILL)
    }
    result.internalTopGap = spacingConfiguration.verticalMediumGap
    result.internalBottomGap = spacingConfiguration.verticalMediumGap

    return result
  }

  override fun groupRowsRange(title: String?, indent: Boolean, topGroupGap: Boolean?, bottomGroupGap: Boolean?,
                              init: Panel.() -> Unit): RowsRangeImpl {
    val result = createRowRange()
    createSeparatorRow(title)
    if (indent) {
      indent(init)
    }
    else {
      init()
    }
    result.endIndex = _rows.size - 1

    setTopGroupGap(rows[result.startIndex], topGroupGap)
    setBottomGroupGap(rows[result.endIndex], bottomGroupGap)

    return result
  }

  override fun collapsibleGroup(title: String, indent: Boolean, init: Panel.() -> Unit): CollapsibleRowImpl {
    val result = CollapsibleRowImpl(dialogPanelConfig, panelContext, this, title) {
      if (indent) {
        indent(init)
      }
      else {
        init()
      }
    }

    result.expanded = false
    result.internalTopGap = spacingConfiguration.verticalMediumGap
    result.internalBottomGap = spacingConfiguration.verticalMediumGap
    _rows.add(result)

    return result
  }

  override fun buttonsGroup(@NlsContexts.Label title: String?, indent: Boolean, init: Panel.() -> Unit): ButtonsGroupImpl {
    val result = ButtonsGroupImpl(this, _rows.size)
    rowsRanges.add(result)

    dialogPanelConfig.context.addButtonsGroup(result)
    try {
      if (title != null) {
        val row = row {
          label(title)
            .applyToComponent { putClientProperty(DslComponentProperty.VERTICAL_COMPONENT_GAP, VerticalComponentGap(bottom = false)) }
        }
        row.internalBottomGap = spacingConfiguration.buttonGroupHeaderBottomGap
      }

      if (indent) {
        indent(init)
      }
      else {
        init()
      }
    }
    finally {
      dialogPanelConfig.context.removeLastButtonsGroup()
    }
    result.endIndex = _rows.size - 1
    return result
  }

  override fun onApply(callback: () -> Unit): PanelImpl {
    dialogPanelConfig.applyCallbacks.list(null).add(callback)
    return this
  }

  override fun onReset(callback: () -> Unit): PanelImpl {
    dialogPanelConfig.resetCallbacks.list(null).add(callback)
    return this
  }

  override fun onIsModified(callback: () -> Boolean): PanelImpl {
    dialogPanelConfig.isModifiedCallbacks.list(null).add(callback)
    return this
  }

  override fun customizeSpacingConfiguration(spacingConfiguration: SpacingConfiguration, init: Panel.() -> Unit) {
    this.spacingConfiguration = spacingConfiguration
    this.init()
  }

  override fun useNewComboBoxRenderer() {
    dialogPanelConfig.useComboBoxNewRenderer = true
  }

  @Deprecated("Use customize(UnscaledGaps) instead")
  @ApiStatus.ScheduledForRemoval
  override fun customize(customGaps: Gaps): PanelImpl {
    return customize(customGaps.toUnscaled())
  }

  override fun customize(customGaps: UnscaledGaps): PanelImpl {
    super.customize(customGaps)
    return this
  }

  override fun enabled(isEnabled: Boolean): PanelImpl {
    enabled = isEnabled
    if (parent == null || parent.isEnabled()) {
      doEnabled(enabled, _rows.indices)
    }
    return this
  }

  override fun enabledFromParent(parentEnabled: Boolean) {
    enabledFromParent(parentEnabled, _rows.indices)
  }

  fun enabledFromParent(parentEnabled: Boolean, range: IntRange) {
    doEnabled(parentEnabled && enabled, range)
  }

  fun isEnabled(row: RowImpl): Boolean {
    val rowIndex = rows.indexOf(row)
    return enabled && isRowFromEnabledRange(rowIndex) && (parent == null || parent.isEnabled())
  }

  override fun enabledIf(predicate: ComponentPredicate): PanelImpl {
    super.enabledIf(predicate)
    return this
  }

  override fun visible(isVisible: Boolean): PanelImpl {
    visible = isVisible
    if (parent == null || parent.isVisible()) {
      doVisible(visible, _rows.indices)
    }
    return this
  }

  override fun visibleIf(predicate: ComponentPredicate): Panel {
    super.visibleIf(predicate)
    return this
  }

  override fun visibleFromParent(parentVisible: Boolean) {
    visibleFromParent(parentVisible, _rows.indices)
  }

  fun visibleFromParent(parentVisible: Boolean, range: IntRange) {
    doVisible(parentVisible && visible, range)
  }

  fun isVisible(row: RowImpl): Boolean {
    val rowIndex = rows.indexOf(row)
    return visible && isRowFromVisibleRange(rowIndex) && (parent == null || parent.isVisible())
  }

  @Deprecated("Use align(AlignX.LEFT/CENTER/RIGHT/FILL) method instead")
  @ApiStatus.ScheduledForRemoval
  override fun horizontalAlign(horizontalAlign: HorizontalAlign): PanelImpl {
    super.horizontalAlign(horizontalAlign)
    return this
  }

  @Deprecated("Use align(AlignY.TOP/CENTER/BOTTOM/FILL) method instead")
  @ApiStatus.ScheduledForRemoval
  override fun verticalAlign(verticalAlign: VerticalAlign): PanelImpl {
    super.verticalAlign(verticalAlign)
    return this
  }

  override fun align(align: Align): PanelImpl {
    super.align(align)
    return this
  }

  override fun resizableColumn(): PanelImpl {
    super.resizableColumn()
    return this
  }

  override fun gap(rightGap: RightGap): PanelImpl {
    super.gap(rightGap)
    return this
  }

  override fun indent(init: Panel.() -> Unit): RowsRangeImpl {
    val result = createRowRange()
    val prevPanelContext = panelContext
    panelContext = panelContext.copy(indentCount = prevPanelContext.indentCount + 1)
    try {
      this.init()
    }
    finally {
      panelContext = prevPanelContext
    }
    result.endIndex = _rows.size - 1
    return result
  }

  private fun doEnabled(isEnabled: Boolean, range: IntRange) {
    for (i in range) {
      _rows[i].enabledFromParent(isEnabled && isRowFromEnabledRange(i))
    }
  }

  private fun isRowFromEnabledRange(rowIndex: Int): Boolean {
    for (rowsRange in rowsRanges) {
      if (rowIndex >= rowsRange.startIndex && rowIndex <= rowsRange.endIndex && !rowsRange.enabled) {
        return false
      }
    }
    return true
  }

  private fun doVisible(isVisible: Boolean, range: IntRange) {
    for (i in range) {
      _rows[i].visibleFromParent(isVisible && isRowFromVisibleRange(i))
    }
  }

  private fun isRowFromVisibleRange(rowIndex: Int): Boolean {
    for (rowsRange in rowsRanges) {
      if (rowIndex >= rowsRange.startIndex && rowIndex <= rowsRange.endIndex && !rowsRange.visible) {
        return false
      }
    }
    return true
  }

  private fun setTopGroupGap(row: RowImpl, topGap: Boolean?) {
    if (topGap == null) {
      row.internalTopGap = spacingConfiguration.verticalMediumGap
    }
    else {
      row.topGap(if (topGap) TopGap.MEDIUM else TopGap.NONE)
    }
  }

  private fun setBottomGroupGap(row: RowImpl, bottomGap: Boolean?) {
    if (bottomGap == null) {
      row.internalBottomGap = spacingConfiguration.verticalMediumGap
    }
    else {
      row.bottomGap(if (bottomGap) BottomGap.MEDIUM else BottomGap.NONE)
    }
  }

  private fun createRowRange(): RowsRangeImpl {
    val result = RowsRangeImpl(this, _rows.size)
    rowsRanges.add(result)
    return result
  }
}

internal data class PanelContext(
  /**
   * Number of [SpacingConfiguration.horizontalIndent] indents before each row in the panel
   */
  val indentCount: Int = 0
)

private fun Panel.createSeparatorRow(@NlsContexts.Separator title: String?): Row {
  return createSeparatorRow(toSeparatorLabel(title))
}

private fun Panel.createSeparatorRow(title: JBLabel?, background: Color? = null): Row {
  val separator: JComponent
  if (title == null) {
    separator = SeparatorComponent(0, 0, background ?: JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground(), null)
  }
  else {
    separator = object : TitledSeparator(title.text) {
      override fun createLabel(): JBLabel {
        return title
      }
    }
    separator.border = null
  }

  return row {
    cell(separator)
      .align(AlignX.FILL)
  }
}

private fun toSeparatorLabel(@NlsContexts.Separator title: String?): JBLabel? {
  @Suppress("DialogTitleCapitalization")
  return if (title == null) null else JBLabel(title)
}