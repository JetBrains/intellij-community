// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.layout

import com.intellij.ui.components.Label
import com.intellij.util.ui.UIUtil.ComponentStyle
import com.intellij.util.ui.UIUtil.FontColor
import javax.swing.JComponent
import javax.swing.JLabel

abstract class Row : Cell() {
  abstract var enabled: Boolean

  abstract var visible: Boolean

  abstract var subRowsEnabled: Boolean

  abstract var subRowsVisible: Boolean

  protected abstract val builder: LayoutBuilderImpl

  fun label(text: String, gapLeft: Int = 0, style: ComponentStyle? = null, fontColor: FontColor? = null, bold: Boolean = false): JLabel {
    val label = Label(text, style, fontColor, bold)
    label(gapLeft = gapLeft)
    return label
  }

  /**
   * Specifies the right alignment for the component if the cell is larger than the component plus its gaps.
   */
  inline fun right(init: Row.() -> Unit) {
    alignRight()
    init()
  }

  @PublishedApi
  internal abstract fun alignRight()

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

  /**
   * Shares cell between components.
   */
  inline fun cell(init: Cell.() -> Unit) {
    setCellMode(true)
    init()
    setCellMode(false)
  }

  @PublishedApi
  internal abstract fun createRow(label: String?): Row

  @PublishedApi
  internal abstract fun setCellMode(value: Boolean)

  @Deprecated(message = "Nested row is prohibited", level = DeprecationLevel.ERROR)
  fun row(label: JLabel? = null, init: Row.() -> Unit) {
  }

  @Deprecated(message = "Nested noteRow is prohibited", level = DeprecationLevel.ERROR)
  fun noteRow(text: String) {
  }

  // override here for backward compatibility
  @Deprecated(level = DeprecationLevel.HIDDEN, message = "deprecated")
  operator fun JComponent.invoke(vararg constraints: CCFlags, gapLeft: Int = 0, growPolicy: GrowPolicy? = null) {
    invoke(constraints = *constraints, gapLeft = gapLeft, growPolicy = growPolicy, comment = null)
  }
}

enum class GrowPolicy {
  SHORT_TEXT, MEDIUM_TEXT
}