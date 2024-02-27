// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui.html

import java.awt.Graphics
import java.awt.Insets
import java.awt.Rectangle
import java.awt.Shape
import javax.swing.text.Element
import javax.swing.text.html.BlockView

/**
 * Supports rounded corners (through `caption-side` CSS property).
 */
class BlockViewEx(elem: Element, axis: Int) : BlockView(elem, axis) {

  // With private fields Java clone doesn't work well
  @Suppress("ProtectedInFinal")
  @JvmField
  protected var borderRadius: Float = -1f

  @Suppress("ProtectedInFinal")
  @JvmField
  protected var borderWidths: Insets? = null

  @Suppress("ProtectedInFinal")
  @JvmField
  protected var borderColors: BorderColors? = null

  @Suppress("ProtectedInFinal")
  @JvmField
  protected var margin: Insets? = null

  override fun setPropertiesFromAttributes() {
    super.setPropertiesFromAttributes()
    borderRadius = cssBorderRadius
    borderWidths = cssBorderWidths
    borderColors = cssBorderColors
    margin = cssMargin
  }

  @Suppress("UseDPIAwareInsets")
  override fun paint(g: Graphics, a: Shape) {
    val painter = this.painter
    val bg = painter.bg
    if (bg != null && borderRadius > 0) {
      paintControlBackgroundAndBorder(
        g, a as Rectangle, bg,
        borderRadius, margin ?: Insets(0, 0, 0, 0),
        borderWidths, borderColors
      )
      painter.bg = null
    }
    super.paint(g, a)
    painter.bg = bg
  }

}