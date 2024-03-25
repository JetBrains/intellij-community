// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui.html

import com.intellij.util.asSafely
import java.awt.Graphics
import java.awt.Insets
import java.awt.Rectangle
import java.awt.Shape
import javax.swing.text.Element
import javax.swing.text.html.BlockView
import javax.swing.text.html.HTML

/**
 * Supports rounded corners through `border-radius` property.
 */
open class BlockViewEx(elem: Element, axis: Int) : BlockView(elem, axis) {

  // With private fields Java clone doesn't work well
  @JvmField
  protected var borderRadius: Float = -1f

  @JvmField
  protected var borderWidths: Insets? = null

  @JvmField
  protected var borderColors: BorderColors? = null

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

  override fun getToolTipText(x: Float, y: Float, allocation: Shape?): String? =
    element.attributes.getAttribute(HTML.Attribute.TITLE)
      ?.asSafely<String>()
      ?.takeIf { it.isNotEmpty() }
    ?: super.getToolTipText(x, y, allocation)

}
