// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui.html

import com.intellij.util.asSafely
import java.awt.Shape
import java.awt.Toolkit
import javax.swing.SizeRequirements
import javax.swing.text.AttributeSet
import javax.swing.text.BoxView
import javax.swing.text.Element
import javax.swing.text.Position.Bias
import javax.swing.text.StyleConstants
import javax.swing.text.StyledDocument
import javax.swing.text.View
import javax.swing.text.ViewFactory
import javax.swing.text.html.CSS
import javax.swing.text.html.HTML
import javax.swing.text.html.ParagraphView
import kotlin.math.max

/**
 * Supports line-height (%, px and no-unit) property in paragraphs. No support for justification.
 */
open class ParagraphViewEx(elem: Element) : ParagraphView(elem) {

  @JvmField
  protected var fixedLineHeight: Float? = null

  @JvmField
  protected var scaledLineHeight: Float? = null

  @JvmField
  protected var fontLineHeight: Int? = null

  @JvmField
  protected var justification: Int = 0

  override fun setPropertiesFromAttributes() {
    super.setPropertiesFromAttributes()

    val lineHeight = attributes.getAttribute(CSS.Attribute.LINE_HEIGHT)
      ?.asSafely<String>()
      ?.trim()

    when {
      lineHeight == null -> {}
      lineHeight.endsWith("%") ->
        lineHeight
          .removeSuffix("%")
          .toFloatOrNull()
          ?.let { scaledLineHeight = it / 100f }
      lineHeight.endsWith("px") ->
        lineHeight
          .removeSuffix("px")
          .toFloatOrNull()
          ?.let { fixedLineHeight = it }
      else ->
        lineHeight
          .toFloatOrNull()
          ?.let { scaledLineHeight = it }
    }
  }

  override fun calculateMinorAxisRequirements(axis: Int, r: SizeRequirements?): SizeRequirements {
    val font = document.asSafely<StyledDocument>()?.getFont(attributes)
               ?: container?.font
    font
      ?.let { container?.getFontMetrics(it) ?: Toolkit.getDefaultToolkit().getFontMetrics(font) }
      .let { fontLineHeight = it?.height }
    return super.calculateMinorAxisRequirements(axis, r)
  }

  override fun createRow(): View {
    return ParagraphRow(element)
  }

  override fun getToolTipText(x: Float, y: Float, allocation: Shape?): String? =
    element.attributes.getAttribute(HTML.Attribute.TITLE)
      ?.asSafely<String>()
      ?.takeIf { it.isNotEmpty() }
    ?: super.getToolTipText(x, y, allocation)

  inner class ParagraphRow internal constructor(elem: Element) : BoxView(elem, X_AXIS) {

    override fun loadChildren(f: ViewFactory) {
    }

    override fun getAttributes(): AttributeSet? =
      parent?.attributes

    override fun getAlignment(axis: Int): Float {
      if (axis == X_AXIS) {
        when (justification) {
          StyleConstants.ALIGN_LEFT -> return 0f
          StyleConstants.ALIGN_RIGHT -> return 1f
          StyleConstants.ALIGN_CENTER -> return 0.5f
          StyleConstants.ALIGN_JUSTIFIED -> return 0f
        }
      }
      return super.getAlignment(axis)
    }

    override fun modelToView(pos: Int, a: Shape, b: Bias): Shape {
      var r = a.bounds
      val v = getViewAtPosition(pos, r)
      if ((v != null) && (!v.element.isLeaf)) {
        return super.modelToView(pos, a, b)
      }
      r = a.bounds
      val height = r.height
      val y = r.y
      val loc = super.modelToView(pos, a, b)
      val bounds = loc.bounds2D
      bounds.setRect(bounds.x, y.toDouble(), bounds.width, height.toDouble())
      return bounds
    }

    override fun getStartOffset(): Int =
      (0 until viewCount).asSequence()
        .map { getView(it) }.minOfOrNull { it.startOffset } ?: Int.MAX_VALUE

    override fun getEndOffset(): Int =
      (0 until viewCount).asSequence()
        .map { getView(it) }.maxOfOrNull { it.endOffset } ?: 0

    override fun layoutMinorAxis(targetSpan: Int, axis: Int, offsets: IntArray, spans: IntArray) {
      baselineLayout(targetSpan, axis, offsets, spans)
    }

    override fun calculateMinorAxisRequirements(axis: Int, r: SizeRequirements?): SizeRequirements =
      baselineRequirements(axis, r)

    override fun getViewIndexAtPosition(pos: Int): Int {
      if (pos < startOffset || pos >= endOffset) return -1
      for (counter in viewCount - 1 downTo 0) {
        val v = getView(counter)
        if (pos >= v.startOffset &&
            pos < v.endOffset) {
          return counter
        }
      }
      return -1
    }

    override fun getLeftInset(): Short {
      var parentView: View
      var adjustment = 0
      if ((parent.also { parentView = it }) != null) { //use firstLineIdent for the first row
        if (this === parentView.getView(0)) {
          adjustment = firstLineIndent
        }
      }
      return (super.getLeftInset() + adjustment).toShort()
    }

    override fun getTopInset(): Short {
      return (super.getTopInset() + lineHeightCorrection / 2).toShort()
    }

    override fun getBottomInset(): Short {
      return (super.getBottomInset() + lineHeightCorrection / 2).toShort()
    }

    private val lineHeightCorrection: Int
      get() {
        val expectedLineHeight = scaledLineHeight?.let { it * (fontLineHeight ?: return 0) }
                                 ?: fixedLineHeight
                                 ?: return 0
        val actualLineHeight = minorRequest?.preferred ?: 0
        return max(0f, expectedLineHeight - actualLineHeight).toInt()
      }
  }

}
