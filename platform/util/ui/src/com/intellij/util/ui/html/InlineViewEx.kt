// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui.html

import com.intellij.util.asSafely
import java.awt.Graphics
import java.awt.Insets
import java.awt.Rectangle
import java.awt.Shape
import javax.swing.text.Element
import javax.swing.text.Position
import javax.swing.text.TabExpander
import javax.swing.text.View
import javax.swing.text.html.HTML
import javax.swing.text.html.InlineView
import kotlin.math.max

/**
 * Supports paddings, margins and rounded corners (through `border-radius` CSS property) for inline elements, like `<span>`.
 *
 * Due to limitations of [HTMLDocument], paddings for nested inline elements are not supported and will cause incorrect rendering.
 */
@Suppress("UseDPIAwareInsets")
internal class InlineViewEx(elem: Element) : InlineView(elem) {

  private lateinit var padding: Insets
  private lateinit var margin: Insets
  private var insets: Insets = Insets(0, 0, 0, 0)

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
  protected var startView: Boolean = false

  @Suppress("ProtectedInFinal")
  @JvmField
  protected var endView: Boolean = false

  private var paintingInTextBounds = false

  override fun viewToModel(x: Float, y: Float, a: Shape, biasReturn: Array<out Position.Bias>): Int {
    return super.viewToModel(x, y, applyInsets(a), biasReturn)
  }

  override fun modelToView(pos: Int, a: Shape, b: Position.Bias): Shape {
    val shape = super.modelToView(pos, a, b)
    if (paintingInTextBounds) {
      return shape
    }
    // When a caret is painted, we get a shape which was not shrunk to text.
    // Add insets to paint the caret in the right position:
    val rect = shape.bounds
    rect.x += insets.left
    rect.y += insets.top
    return rect
  }

  override fun setPropertiesFromAttributes() {
    super.setPropertiesFromAttributes()
    borderRadius = cssBorderRadius
    borderWidths = cssBorderWidths
    borderColors = cssBorderColors
    updatePaddingsAndMargins(true)
  }

  override fun getPartialSpan(p0: Int, p1: Int): Float {
    var offset = 0
    if (p0 == startOffset && p0 != p1) {
      offset += insets.left
    }
    if (p1 == endOffset && p0 != p1) {
      offset += insets.right
    }
    return offset + super.getPartialSpan(p0, p1)
  }

  override fun getPreferredSpan(axis: Int): Float {
    updatePaddingsAndMargins(false)
    return super.getPreferredSpan(axis) + when (axis) {
      View.X_AXIS -> insets.width
      View.Y_AXIS -> insets.height
      else -> throw IllegalArgumentException("Invalid axis: $axis")
    }
  }

  override fun getTabbedSpan(x: Float, e: TabExpander?): Float =
    super.getTabbedSpan(x, e) + insets.width

  override fun getBreakWeight(axis: Int, pos: Float, len: Float): Int =
    super.getBreakWeight(axis, adjustedBreakPos(axis, pos), adjustedBreakLen(axis, len))

  override fun breakView(axis: Int, offset: Int, pos: Float, len: Float): View =
    super.breakView(axis, offset, adjustedBreakPos(axis, pos), adjustedBreakLen(axis, len))

  private fun adjustedBreakPos(axis: Int, pos: Float): Float =
    max(pos - if (axis == View.X_AXIS) insets.left else insets.top, 0f)

  private fun adjustedBreakLen(axis: Int, pos: Float): Float =
    max(pos - if (axis == View.X_AXIS) insets.width else insets.height, 0f)

  override fun paint(g: Graphics, a: Shape) {
    val bg = getBackground()

    paintControlBackgroundAndBorder(
      g, a as Rectangle,
      bg, borderRadius, margin, borderWidths, borderColors,
      noBorderOnTheRight = !endView,
      noBorderOnTheLeft = !startView,
    )

    try {
      background = null
      paintingInTextBounds = true
      super.paint(g, applyInsets(a))
    }
    finally {
      paintingInTextBounds = false
      background = bg
    }
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
      return (insets.top + (contentsAlign * h)) / (insets.height + h)
    }
    return super.getAlignment(axis)
  }

  override fun getToolTipText(x: Float, y: Float, allocation: Shape?): String? =
    element.attributes.getAttribute(HTML.Attribute.TITLE)
      ?.asSafely<String>()
      ?.takeIf { it.isNotEmpty() }
    ?: super.getToolTipText(x, y, allocation)

  private fun applyInsets(shape: Shape): Rectangle {
    // Shrink by padding and margin
    val result = shape.bounds
    result.x += insets.left
    result.y += insets.top
    result.width -= insets.width
    result.height -= insets.height
    return result
  }

  private fun getSibling(parentView: View, curIndex: Int, direction: Int): View? {
    var siblingIndex = curIndex + direction
    val viewCount = parentView.viewCount
    while (siblingIndex in 0..<viewCount) {
      parentView.getView(siblingIndex)
        .takeIf { it.element?.name?.equals("wbr", true) != true }
        ?.let { return it }
      siblingIndex += direction
    }
    return null
  }

  private fun updatePaddingsAndMargins(force: Boolean) {
    val parentView = parent

    val cssPadding = this.cssPadding
    val cssMargin = this.cssMargin

    val startView: Boolean
    val endView: Boolean
    if (parentView == null) {
      startView = true
      endView = true
    } else {
      val index = (0..<parentView.viewCount).firstOrNull { parentView.getView(it) === this } ?: -1

      // Heuristics to determine whether we are within the same inline (e.g. <span>) element with paddings.
      // Nested inline element insets are not supported, because hierarchy of inline elements is not preserved.
      val prevSibling = getSibling(parentView, index, -1)
      val nextSibling = getSibling(parentView, index, 1)

      startView = prevSibling?.cssPadding != cssPadding || prevSibling.cssMargin != cssMargin
      endView = nextSibling?.cssPadding != cssPadding || nextSibling.cssMargin != cssMargin
    }

    if (!force && startView == this.startView && endView == this.endView) {
      return
    }
    this.startView = startView
    this.endView = endView

    padding = Insets(
      cssPadding.top,
      if (startView) cssPadding.left else 0,
      cssPadding.bottom,
      if (endView) cssPadding.right else 0,
    )

    margin = Insets(
      cssMargin.top,
      if (startView) cssMargin.left else 0,
      cssMargin.bottom,
      if (endView) cssMargin.right else 0,
    )

    insets = Insets(
      padding.top + margin.top,
      padding.left + margin.left,
      padding.bottom + margin.bottom,
      padding.right + margin.right,
    )
  }



}
