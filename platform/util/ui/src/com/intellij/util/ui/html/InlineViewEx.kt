// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui.html

import com.intellij.util.asSafely
import java.awt.Graphics
import java.awt.Insets
import java.awt.Rectangle
import java.awt.Shape
import javax.swing.text.Element
import javax.swing.text.TabExpander
import javax.swing.text.View
import javax.swing.text.html.CSS
import javax.swing.text.html.HTMLDocument
import javax.swing.text.html.InlineView
import kotlin.math.max

private val CAPTION_SIDE = CSS.Attribute::class.java
  .getDeclaredField("CAPTION_SIDE")
  .apply { isAccessible = true }
  .get(null)

/**
 * Supports paddings and margins for inline elements, like `<span>`. Due to limitations of [HTMLDocument],
 * paddings for nested inline elements are not supported and will cause incorrect rendering.
 */
@Suppress("UseDPIAwareInsets")
internal class InlineViewEx(elem: Element) : InlineView(elem) {

  private lateinit var padding: Insets
  private lateinit var margin: Insets
  private var insets: Insets = Insets(0, 0, 0, 0)

  // With private fields Java clone doesn't work well
  @Suppress("ProtectedInFinal")
  @JvmField
  protected var borderRadius: Int = -1

  @Suppress("ProtectedInFinal")
  @JvmField
  protected var startView: Boolean = false

  @Suppress("ProtectedInFinal")
  @JvmField
  protected var endView: Boolean = false

  override fun setPropertiesFromAttributes() {
    super.setPropertiesFromAttributes()


    // "caption-side" is used as "border-radius"
    borderRadius = attributes.getAttribute(CAPTION_SIDE)
                     ?.asSafely<String>()
                     ?.takeIf { it.endsWith("px") }
                     ?.removeSuffix("px")
                     ?.toIntOrNull()
                   ?: 0
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
      View.X_AXIS -> insets.width()
      View.Y_AXIS -> insets.height()
      else -> throw IllegalArgumentException("Invalid axis: $axis")
    }
  }

  override fun getTabbedSpan(x: Float, e: TabExpander?): Float =
    super.getTabbedSpan(x, e) + insets.width()

  override fun getBreakWeight(axis: Int, pos: Float, len: Float): Int =
    super.getBreakWeight(axis, adjustedBreakPos(axis, pos), adjustedBreakLen(axis, len))

  override fun breakView(axis: Int, offset: Int, pos: Float, len: Float): View =
    super.breakView(axis, offset, adjustedBreakPos(axis, pos), adjustedBreakLen(axis, len))

  private fun adjustedBreakPos(axis: Int, pos: Float): Float =
    max(pos - if (axis == View.X_AXIS) insets.left else insets.top, 0f)

  private fun adjustedBreakLen(axis: Int, pos: Float): Float =
    max(pos - if (axis == View.X_AXIS) insets.width() else insets.height(), 0f)

  override fun paint(g: Graphics, a: Shape) {
    val alloc = if (a is Rectangle) a else a.bounds
    // Shrink by margin
    alloc.setBounds(alloc.x + margin.left, alloc.y + margin.top,
                    alloc.width - margin.width(),
                    alloc.height - margin.height())
    val bg = getBackground()
    if (bg != null) {
      g.color = bg
      if (borderRadius > 0 && (startView || endView)) {
        g.fillRoundRect(alloc.x, alloc.y, alloc.width, alloc.height, borderRadius, borderRadius)
        if (!startView) {
          g.fillRect(alloc.x, alloc.y, alloc.width - borderRadius, alloc.height)
        }
        else if (!endView) {
          g.fillRect(alloc.x + borderRadius, alloc.y, alloc.width, alloc.height)
        }
      }
      else {
        g.fillRect(alloc.x, alloc.y, alloc.width, alloc.height)
      }
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
      return (insets.top + (contentsAlign * h)) / (insets.height() + h)
    }
    return super.getAlignment(axis)
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
    val index = (0..<parentView.viewCount).firstOrNull { parentView.getView(it) === this } ?: -1

    // Heuristics to determine whether we are within the same inline (e.g. <span>) element with paddings.
    // Nested inline element insets are not supported, because hierarchy of inline elements is not preserved.
    val prevSibling = getSibling(parentView, index, -1)
    val nextSibling = getSibling(parentView, index, 1)

    val cssPadding = this.cssPadding
    val cssMargin = this.cssMargin

    val startView = prevSibling?.cssPadding != cssPadding || prevSibling.cssMargin != cssMargin
    val endView = nextSibling?.cssPadding != cssPadding || nextSibling.cssMargin != cssMargin

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

  fun Insets.width() =
    right + left

  fun Insets.height() =
    top + bottom

}