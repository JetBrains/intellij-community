// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.dsl.impl

import com.intellij.openapi.ui.OnePixelDivider
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.SeparatorComponent
import com.intellij.ui.TitledSeparator
import com.intellij.ui.components.Label
import com.intellij.ui.dsl.*
import com.intellij.ui.dsl.Row
import com.intellij.ui.dsl.SpacingConfiguration
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.ui.dsl.gridLayout.UiDslException
import com.intellij.ui.dsl.gridLayout.VerticalAlign
import com.intellij.ui.layout.*
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent
import javax.swing.JLabel

@ApiStatus.Internal
internal class PanelImpl(private val dialogPanelConfig: DialogPanelConfig) : CellBaseImpl<Panel>(), Panel {

  val rows: List<RowImpl>
    get() = _rows

  private var panelContext = PanelContext()

  private val _rows = mutableListOf<RowImpl>()

  override fun enabled(isEnabled: Boolean): PanelImpl {
    return enabled(isEnabled, _rows.indices)
  }

  fun enabled(isEnabled: Boolean, range: IntRange): PanelImpl {
    for (i in range) {
      _rows[i].enabled(isEnabled)
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
    _rows.add(result)
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
    val result = RowsRangeImpl(this, _rows.size)
    val component = createSeparator(title)
    row {
      cell(component)
        .horizontalAlign(HorizontalAlign.FILL)
    }.gap(TopGap.GROUP)
    indent(init)
    result.endIndex = _rows.size - 1
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
    return visible(isVisible, _rows.indices)
  }

  fun visible(isVisible: Boolean, range: IntRange): PanelImpl {
    for (i in range) {
      _rows[i].visible(isVisible)
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
}

internal data class PanelContext(
  /**
   * Number of [SpacingConfiguration.horizontalIndent] indents before each row in the panel
   */
  val indentCount: Int = 0
)
