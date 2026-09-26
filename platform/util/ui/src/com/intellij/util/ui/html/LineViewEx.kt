package com.intellij.util.ui.html

import javax.swing.text.Element
import javax.swing.text.html.CSS

/**
 * Supports line-height property
 */
internal class LineViewEx(elem: Element) : ParagraphViewEx(elem) {

  private var preWrap = false

  override fun isVisible(): Boolean = true

  override fun getMinimumSpan(axis: Int): Float =
    if (preWrap) super.getMinimumSpan(axis) else getPreferredSpan(axis)

  override fun getResizeWeight(axis: Int): Int =
    when (axis) {
      X_AXIS -> 1
      Y_AXIS -> 0
      else -> throw IllegalArgumentException("Invalid axis: $axis")
    }

  override fun getAlignment(axis: Int): Float =
    if (axis == X_AXIS) 0f else super.getAlignment(axis)

  override fun layout(width: Int, height: Int) {
    super.layout(if (preWrap) width else Int.MAX_VALUE - 1, height)
  }

  override fun setPropertiesFromAttributes() {
    super.setPropertiesFromAttributes()
    preWrap = parent?.attributes?.containsAttribute(CSS.Attribute.WHITE_SPACE, "pre-wrap") == true
  }

}
