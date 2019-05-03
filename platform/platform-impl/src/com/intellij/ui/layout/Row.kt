// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.layout

import com.intellij.openapi.ui.panel.ComponentPanelBuilder
import com.intellij.ui.components.Label
import com.intellij.ui.components.RadioButton
import com.intellij.ui.components.noteComponent
import com.intellij.util.ui.UIUtil.ComponentStyle
import com.intellij.util.ui.UIUtil.FontColor
import javax.swing.ButtonGroup
import javax.swing.JComponent

abstract class Row : Cell() {
  abstract var enabled: Boolean

  abstract var visible: Boolean

  abstract var subRowsEnabled: Boolean

  abstract var subRowsVisible: Boolean

  protected abstract val builder: LayoutBuilderImpl

  /**
   * Specifies the right alignment for the component if the cell is larger than the component plus its gaps.
   */
  inline fun right(init: Row.() -> Unit) {
    alignRight()
    init()
  }

  @PublishedApi
  internal abstract fun alignRight()

  abstract fun largeGapAfter()

  inline fun row(label: String, init: Row.() -> Unit): Row {
    val row = createRow(label)
    row.init()
    return row
  }

  inline fun row(init: Row.() -> Unit): Row {
    val row = createRow(null)
    row.init()
    return row
  }

  inline fun buttonGroup(init: Row.() -> Unit): Row {
    val row = createRow(null, ButtonGroup())
    row.init()
    return row
  }

  /**
   * Shares cell between components.
   */
  inline fun cell(isVerticalFlow: Boolean = false, init: Cell.() -> Unit) {
    setCellMode(true, isVerticalFlow)
    init()
    setCellMode(false, isVerticalFlow)
  }

  @PublishedApi
  internal abstract fun createRow(label: String?): Row

  @PublishedApi
  internal abstract fun createRow(label: String?, buttonGroup: ButtonGroup?): Row

  @PublishedApi
  internal abstract fun createNoteOrCommentRow(component: JComponent): Row

  @PublishedApi
  internal abstract fun setCellMode(value: Boolean, isVerticalFlow: Boolean)

  // backward compatibility
  @Deprecated(level = DeprecationLevel.HIDDEN, message = "deprecated")
  operator fun JComponent.invoke(vararg constraints: CCFlags, gapLeft: Int = 0, growPolicy: GrowPolicy? = null) {
    invoke(constraints = *constraints, gapLeft = gapLeft, growPolicy = growPolicy, comment = null)
  }

  @Deprecated(level = DeprecationLevel.ERROR, message = "Do not create standalone panel, if you want layout components in vertical flow mode, use cell(isVerticalFlow = true)")
  fun panel(vararg constraints: LCFlags, title: String? = null, init: LayoutBuilder.() -> Unit) {
  }
}

enum class GrowPolicy {
  SHORT_TEXT, MEDIUM_TEXT
}

fun Row.enableIf(predicate: ComponentPredicate) {
  enabled = predicate()
  predicate.addListener { enabled = it }
}
