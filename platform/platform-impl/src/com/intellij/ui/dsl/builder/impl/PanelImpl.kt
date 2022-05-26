// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.dsl.builder.impl

import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.ui.OnePixelDivider
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.SeparatorComponent
import com.intellij.ui.TitledSeparator
import com.intellij.ui.components.Label
import com.intellij.ui.dsl.UiDslException
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.builder.SpacingConfiguration
import com.intellij.ui.dsl.gridLayout.Gaps
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.ui.dsl.gridLayout.VerticalAlign
import com.intellij.ui.layout.*
import org.jetbrains.annotations.ApiStatus
import java.awt.Color
import javax.swing.JComponent
import javax.swing.JLabel

// todo remove 'open' in version 2022.2
@ApiStatus.Internal
internal open class PanelImpl(private val dialogPanelConfig: DialogPanelConfig,
                         private val parent: RowImpl?) : CellBaseImpl<Panel>(), Panel {

  val rows: List<RowImpl>
    get() = _rows

  var spacingConfiguration: SpacingConfiguration? = null
    private set

  private var panelContext = PanelContext()

  private val _rows = mutableListOf<RowImpl>()

  private var visible = true
  private var enabled = true

  override fun row(label: String, init: Row.() -> Unit): RowImpl {
    if (label === EMPTY_LABEL) {
      val result = RowImpl(dialogPanelConfig, panelContext, this, RowLayout.LABEL_ALIGNED)
      result.cell()
      result.init()
      _rows.add(result)
      return result
    }
    else {
      if (label.isEmpty()) {
        warn("Row is created with empty label")
      }

      return row(Label(label), init)
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
      }
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

  override fun separator(@NlsContexts.Separator title: String?, background: Color?): Row {
    val separator = createSeparator(title, background)
    return row {
      cell(separator)
        .horizontalAlign(HorizontalAlign.FILL)
    }
  }

  override fun panel(init: Panel.() -> Unit): PanelImpl {
    lateinit var result: PanelImpl
    row {
      result = panel(init).verticalAlign(VerticalAlign.FILL) as PanelImpl
    }
    return result
  }

  override fun rowsRange(init: Panel.() -> Unit): RowsRangeImpl {
    val result = RowsRangeImpl(this, _rows.size)
    this.init()
    result.endIndex = _rows.size - 1
    return result
  }

  override fun group(title: String?, indent: Boolean, init: Panel.() -> Unit): RowImpl {
    val result = row {
      panel {
        separator(title)
        if (indent) {
          indent(init)
        }
        else {
          init()
        }
      }.verticalAlign(VerticalAlign.FILL)
    }
    result.internalTopGap = dialogPanelConfig.spacing.verticalMediumGap
    result.internalBottomGap = dialogPanelConfig.spacing.verticalMediumGap

    return result
  }

  override fun group(title: String?, indent: Boolean, topGroupGap: Boolean?, bottomGroupGap: Boolean?, init: Panel.() -> Unit): Panel {
    lateinit var result: Panel
    val separator = createSeparator(title)
    val row = row {
      result = panel {
        row {
          cell(separator)
            .horizontalAlign(HorizontalAlign.FILL)
        }
      }
    }

    if (indent) {
      result.indent(init)
    }
    else {
      result.init()
    }

    setTopGroupGap(row, topGroupGap)
    setBottomGroupGap(row, bottomGroupGap)

    return result
  }

  override fun groupRowsRange(title: String?, indent: Boolean, topGroupGap: Boolean?, bottomGroupGap: Boolean?,
                              init: Panel.() -> Unit): RowsRangeImpl {
    val result = RowsRangeImpl(this, _rows.size)
    val separator = createSeparator(title)
    row {
      cell(separator)
        .horizontalAlign(HorizontalAlign.FILL)
    }
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
    result.internalTopGap = dialogPanelConfig.spacing.verticalMediumGap
    result.internalBottomGap = dialogPanelConfig.spacing.verticalMediumGap
    _rows.add(result)

    return result
  }

  override fun collapsibleGroup(title: String,
                                indent: Boolean,
                                topGroupGap: Boolean?,
                                bottomGroupGap: Boolean?,
                                init: Panel.() -> Unit): CollapsiblePanel {
    val row = row { }
    val result = CollapsiblePanelImpl(dialogPanelConfig, row, title) {
      if (indent) {
        indent(init)
      }
      else {
        init()
      }
    }

    result.expanded = false
    row.cell(result)

    setTopGroupGap(row, topGroupGap)
    setBottomGroupGap(row, bottomGroupGap)

    return result
  }

  override fun buttonGroup(title: String?, indent: Boolean, init: Panel.() -> Unit) {
    buttonsGroup(title, indent, init)
  }

  override fun <T> buttonGroup(binding: PropertyBinding<T>, type: Class<T>, title: String?, indent: Boolean, init: Panel.() -> Unit) {
    buttonsGroup(title, indent, init)
      .bind(binding, type)
  }

  override fun buttonsGroup(title: String?, indent: Boolean, init: Panel.() -> Unit): ButtonsGroupImpl {
    val result = ButtonsGroupImpl()
    dialogPanelConfig.context.addButtonsGroup(result)
    try {
      if (title != null) {
        val row = row {
          label(title)
            .applyToComponent { putClientProperty(DslComponentPropertyInternal.LABEL_NO_BOTTOM_GAP, true) }
        }
        row.internalBottomGap = dialogPanelConfig.spacing.buttonGroupHeaderBottomGap
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
    return result
  }

  override fun onApply(callback: () -> Unit): PanelImpl {
    dialogPanelConfig.applyCallbacks.register(null, callback)
    return this
  }

  override fun onReset(callback: () -> Unit): PanelImpl {
    dialogPanelConfig.resetCallbacks.register(null, callback)
    return this
  }

  override fun onIsModified(callback: () -> Boolean): PanelImpl {
    dialogPanelConfig.isModifiedCallbacks.register(null, callback)
    return this
  }

  override fun customizeSpacingConfiguration(spacingConfiguration: SpacingConfiguration, init: Panel.() -> Unit) {
    val prevSpacingConfiguration = dialogPanelConfig.spacing
    dialogPanelConfig.spacing = spacingConfiguration
    this.spacingConfiguration = spacingConfiguration
    try {
      this.init()
    }
    finally {
      dialogPanelConfig.spacing = prevSpacingConfiguration
    }
  }

  override fun customize(customGaps: Gaps): PanelImpl {
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

  fun isEnabled(): Boolean {
    return enabled && (parent == null || parent.isEnabled())
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

  fun isVisible(): Boolean {
    return visible && (parent == null || parent.isVisible())
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

  override fun gap(rightGap: RightGap): PanelImpl {
    super.gap(rightGap)
    return this
  }

  override fun indent(init: Panel.() -> Unit): RowsRangeImpl {
    val result = RowsRangeImpl(this, _rows.size)
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

  private fun createSeparator(@NlsContexts.BorderTitle title: String?, background: Color? = null): JComponent {
    if (title == null) {
      return SeparatorComponent(0, background ?: OnePixelDivider.BACKGROUND, null)
    }

    val result = TitledSeparator(title)
    result.border = null
    return result
  }

  private fun doEnabled(isEnabled: Boolean, range: IntRange) {
    for (i in range) {
      _rows[i].enabledFromParent(isEnabled)
    }
  }

  private fun doVisible(isVisible: Boolean, range: IntRange) {
    for (i in range) {
      _rows[i].visibleFromParent(isVisible)
    }
  }

  private fun setTopGroupGap(row: RowImpl, topGap: Boolean?) {
    if (topGap == null) {
      row.internalTopGap = dialogPanelConfig.spacing.verticalMediumGap
    }
    else {
      row.topGap(if (topGap) TopGap.MEDIUM else TopGap.NONE)
    }
  }

  private fun setBottomGroupGap(row: RowImpl, bottomGap: Boolean?) {
    if (bottomGap == null) {
      row.internalBottomGap = dialogPanelConfig.spacing.verticalMediumGap
    }
    else {
      row.bottomGap(if (bottomGap) BottomGap.MEDIUM else BottomGap.NONE)
    }
  }

  override fun validationRequestor(validationRequestor: (() -> Unit) -> Unit): PanelImpl {
    dialogPanelConfig.validationRequestors.add(validationRequestor)
    return this
  }
}

internal data class PanelContext(
  /**
   * Number of [SpacingConfiguration.horizontalIndent] indents before each row in the panel
   */
  val indentCount: Int = 0
)
