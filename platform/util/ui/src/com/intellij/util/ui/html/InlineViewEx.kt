// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui.html

import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import java.awt.Graphics
import java.awt.Rectangle
import java.awt.Shape
import javax.swing.text.AttributeSet
import javax.swing.text.Element
import javax.swing.text.View
import javax.swing.text.html.CSS
import javax.swing.text.html.HTMLDocument
import javax.swing.text.html.HTMLDocument.BlockElement
import javax.swing.text.html.InlineView
import javax.swing.text.html.StyleSheet

/**
 * Supports paddings and margins for inline elements, like `<span>`. Due to limitations of [HTMLDocument],
 * paddings for nested inline elements are not supported and will cause incorrect rendering.
 */
class InlineViewEx(elem: Element) : InlineView(elem) {

  private lateinit var padding: JBInsets
  private lateinit var margin: JBInsets


  override fun setPropertiesFromAttributes() {
    super.setPropertiesFromAttributes()

    val parent = element.parentElement as BlockElement
    val index = parent.getElementIndex(element.startOffset)

    // Heuristics to determine whether we are within the same inline (e.g. <span>) element with paddings.
    // Nested inline element insets are not supported, because hierarchy of inline elements is not preserved.
    val prevSibling = if (index > 0) parent.getElement(index - 1) else null
    val nextSibling = if (index < parent.elementCount - 1) parent.getElement(index + 1) else null

    padding = element.padding
    margin = element.margin

    val startView = prevSibling?.padding != padding || prevSibling.margin != margin
    val endView = nextSibling?.padding != padding || nextSibling.margin != margin

    padding.set(
      padding.top,
      if (startView) padding.left else 0,
      padding.bottom,
      if (endView) padding.right else 0,
    )

    margin.set(
      margin.top,
      if (startView) margin.left else 0,
      margin.bottom,
      if (endView) margin.right else 0,
    )
  }

  private val Element.padding: JBInsets
    get() {
      val styleSheet = getStyleSheet()
      return JBUI.insets(
        attributes.getLength(CSS.Attribute.PADDING_TOP, styleSheet).toInt(),
        attributes.getLength(CSS.Attribute.PADDING_LEFT, styleSheet).toInt(),
        attributes.getLength(CSS.Attribute.PADDING_BOTTOM, styleSheet).toInt(),
        attributes.getLength(CSS.Attribute.PADDING_RIGHT, styleSheet).toInt(),
      )
    }

  private val Element.margin: JBInsets
    get() {
      val styleSheet = getStyleSheet()
      return JBUI.insets(
        attributes.getLength(CSS.Attribute.MARGIN_TOP, styleSheet).toInt(),
        attributes.getLength(CSS.Attribute.MARGIN_LEFT, styleSheet).toInt(),
        attributes.getLength(CSS.Attribute.MARGIN_BOTTOM, styleSheet).toInt(),
        attributes.getLength(CSS.Attribute.MARGIN_RIGHT, styleSheet).toInt(),
      )
    }

  private fun AttributeSet.getLength(attribute: CSS.Attribute, styleSheet: StyleSheet): Float =
    cssLength.invoke(css, this, attribute, styleSheet) as Float

  override fun getPartialSpan(p0: Int, p1: Int): Float {
    val offset = when {
      p0 == startOffset -> padding.left + margin.left
      p1 == endOffset -> padding.right + margin.right
      else -> 0
    }
    return offset + super.getPartialSpan(p0, p1)
  }

  override fun getPreferredSpan(axis: Int): Float =
    super.getPreferredSpan(axis) + when (axis) {
      View.X_AXIS -> padding.width() + margin.width()
      View.Y_AXIS -> padding.height() + margin.height()
      else -> throw IllegalArgumentException("Invalid axis: $axis")
    }

  override fun getMinimumSpan(axis: Int): Float =
    if (axis == Y_AXIS)
      super.getMinimumSpan(axis) + padding.height() + margin.height()
    else
      super.getMinimumSpan(axis)

  override fun paint(g: Graphics, a: Shape) {
    val alloc = if (a is Rectangle) a else a.bounds
    // Shrink by margin
    alloc.setBounds(alloc.x + margin.left, alloc.y + margin.top,
                    alloc.width - margin.width(),
                    alloc.height - margin.height())
    val bg = getBackground()
    if (bg != null) {
      g.color = bg
      g.fillRect(alloc.x, alloc.y, alloc.width, alloc.height)
    }
    // Shrink by padding
    alloc.setBounds(alloc.x + padding.left, alloc.y + padding.top,
                    alloc.width - padding.width(),
                    alloc.height - padding.height())
    super.paint(g, alloc)
  }

  override fun getAlignment(axis: Int): Float {
    checkPainter()
    if (axis == Y_AXIS) {
      val painter = glyphPainter
      val sup = isSuperscript()
      val sub = isSubscript()
      val h = painter.getHeight(this)
      val d = painter.getDescent(this)
      val a = painter.getAscent(this)
      val contentsAlign = when {
        sup -> 1.0f
        sub -> if (h > 0) (h - (d + a / 2)) / h else 0f
        else -> if (h > 0) (h - d) / h else 0f
      }
      return (padding.top + margin.top + (contentsAlign * h)) / (padding.height() + margin.height() + h)
    }
    return super.getAlignment(axis)
  }

  companion object {
    private val css: CSS by lazy(LazyThreadSafetyMode.PUBLICATION) { CSS() }
    private val cssLength by lazy(LazyThreadSafetyMode.PUBLICATION) {
      CSS::class.java.getDeclaredMethod("getLength", AttributeSet::class.java, CSS.Attribute::class.java, StyleSheet::class.java)
        .also { it.isAccessible = true }
    }

  }

}