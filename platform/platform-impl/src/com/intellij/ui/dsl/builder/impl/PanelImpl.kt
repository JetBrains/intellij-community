// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.dsl.builder.impl

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
import javax.swing.JComponent
import javax.swing.JLabel

@ApiStatus.Internal
internal open class PanelImpl(private val dialogPanelConfig: DialogPanelConfig) : CellBaseImpl<Panel>(), Panel {

  val rows: List<RowImpl>
    get() = _rows

  var spacingConfiguration: SpacingConfiguration? = null
    private set

  var customGaps: Gaps? = null
    private set

  private var panelContext = PanelContext()

  private val _rows = mutableListOf<RowImpl>()

  private var visibleDependentProperty = ParentDependentProperty(true)
  private var enabledDependentProperty = ParentDependentProperty(true)

  override fun row(label: String, init: Row.() -> Unit): RowImpl {
    return row(Label(label), init)
  }

  override fun row(label: JLabel?, init: Row.() -> Unit): RowImpl {
    val result = RowImpl(dialogPanelConfig, panelContext, label)
    result.init()
    _rows.add(result)

    if (label != null && result.cells.size > 1) {
      labelCell(label, result.cells[1])
    }

    return result
  }

  override fun twoColumnRow(column1: (Row.() -> Unit)?, column2: (Row.() -> Unit)?): Row {
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

  override fun panel(init: Panel.() -> Unit): PanelImpl {
    val result = PanelImpl(dialogPanelConfig)
    result.init()
    row { }.cell(result)
    return result
  }

  override fun rowsRange(init: Panel.() -> Unit): RowsRangeImpl {
    val result = RowsRangeImpl(this, _rows.size)
    this.init()
    result.endIndex = _rows.size - 1
    return result
  }

  override fun group(title: String?, indent: Boolean, init: Panel.() -> Unit): PanelImpl {
    val component = createSeparator(title)
    val groupTopGap = dialogPanelConfig.spacing.groupTopGap
    val result = panel {
      val row = row {
        cell(component)
          .horizontalAlign(HorizontalAlign.FILL)
      } as RowImpl
      row.internalTopGap = groupTopGap
    }

    if (indent) {
      result.indent(init)
    } else {
      result.init()
    }
    return result
  }

  override fun groupRowsRange(title: String?, init: Panel.() -> Unit): RowsRangeImpl {
    val result = RowsRangeImpl(this, _rows.size)
    val component = createSeparator(title)
    val row = row {
      cell(component)
        .horizontalAlign(HorizontalAlign.FILL)
    }
    row.internalTopGap = dialogPanelConfig.spacing.groupTopGap
    indent(init)
    result.endIndex = _rows.size - 1
    return result
  }

  override fun hideableGroup(title: String, indent: Boolean, init: Panel.() -> Unit): HideablePanelImpl {
    val result = HideablePanelImpl(dialogPanelConfig, title)
    if (indent) {
      result.indent(init)
    } else {
      result.init()
    }
    result.collapse()
    row { }.cell(result)
    return result
  }

  override fun <T> buttonGroup(binding: PropertyBinding<T>, type: Class<T>, title: String?, init: Panel.() -> Unit) {
    dialogPanelConfig.context.addButtonGroup(BindButtonGroup(binding, type))
    try {
      if (title != null) {
        val row = row {
          label(title)
            .applyToComponent { putClientProperty(DSL_LABEL_NO_BOTTOM_GAP_PROPERTY, true) }
        }
        row.internalBottomGap = dialogPanelConfig.spacing.buttonGroupHeaderBottomGap
      }
      indent(init)
    }
    finally {
      dialogPanelConfig.context.removeLastButtonGroup()
    }
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

  override fun customize(customGaps: Gaps): Panel {
    this.customGaps = customGaps
    return this
  }

  override fun enabled(isEnabled: Boolean): PanelImpl {
    enabledDependentProperty.value = isEnabled
    if (!enabledDependentProperty.isParentValue) {
      doEnabled(isEnabled, _rows.indices)
    }
    return this
  }

  fun enabledFromParent(isEnabled: Boolean) {
    enabledFromParent(isEnabled, _rows.indices)
  }

  fun enabledFromParent(isEnabled: Boolean, range: IntRange) {
    if (isEnabled) {
      enabledDependentProperty.parentValue = null
      doEnabled(enabledDependentProperty.value, range)
    }
    else {
      enabledDependentProperty.parentValue = false
      doEnabled(false, range)
    }
  }

  override fun enabledIf(predicate: ComponentPredicate): PanelImpl {
    enabled(predicate())
    predicate.addListener { enabled(it) }
    return this
  }

  override fun visible(isVisible: Boolean): PanelImpl {
    visibleDependentProperty.value = isVisible
    if (!visibleDependentProperty.isParentValue) {
      doVisible(isVisible, _rows.indices)
    }
    return this
  }

  fun visibleFromParent(isVisible: Boolean) {
    visibleFromParent(isVisible, _rows.indices)
  }

  fun visibleFromParent(isVisible: Boolean, range: IntRange) {
    if (isVisible) {
      visibleDependentProperty.parentValue = null
      doVisible(visibleDependentProperty.value, range)
    }
    else {
      visibleDependentProperty.parentValue = false
      doVisible(false, range)
    }
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

  override fun indent(init: Panel.() -> Unit) {
    val prevPanelContext = panelContext
    panelContext = panelContext.copy(indentCount = prevPanelContext.indentCount + 1)
    try {
      this.init()
    }
    finally {
      panelContext = prevPanelContext
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
}

internal data class PanelContext(
  /**
   * Number of [SpacingConfiguration.horizontalIndent] indents before each row in the panel
   */
  val indentCount: Int = 0
)
