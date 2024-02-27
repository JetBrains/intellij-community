// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui.html

import com.intellij.util.asSafely
import java.awt.*
import java.awt.geom.RoundRectangle2D
import java.util.*
import javax.swing.SizeRequirements
import javax.swing.text.AttributeSet
import javax.swing.text.BoxView
import javax.swing.text.GlyphView
import javax.swing.text.View
import javax.swing.text.html.BlockView
import javax.swing.text.html.CSS
import javax.swing.text.html.HTMLDocument
import javax.swing.text.html.StyleSheet
import javax.swing.text.html.StyleSheet.BoxPainter

val CSS_ATTRIBUTE_CAPTION_SIDE: CSS.Attribute
  get() = CAPTION_SIDE_FIELD as CSS.Attribute

private val CAPTION_SIDE_FIELD = CSS.Attribute::class.java
  .getDeclaredField("CAPTION_SIDE")
  .apply { isAccessible = true }
  .get(null)

@Suppress("UseDPIAwareInsets")
val View.cssPadding: Insets
  get() {
    val styleSheet = (document as HTMLDocument).styleSheet
    val attributeSet = attributes ?: return Insets(0, 0, 0, 0)
    return Insets(
      attributeSet.getLength(CSS.Attribute.PADDING_TOP, styleSheet).toInt(),
      attributeSet.getLength(CSS.Attribute.PADDING_LEFT, styleSheet).toInt(),
      attributeSet.getLength(CSS.Attribute.PADDING_BOTTOM, styleSheet).toInt(),
      attributeSet.getLength(CSS.Attribute.PADDING_RIGHT, styleSheet).toInt(),
    )
  }

@Suppress("UseDPIAwareInsets")
val View.cssMargin: Insets
  get() {
    val styleSheet = (document as HTMLDocument).styleSheet
    val attributeSet = attributes ?: return Insets(0, 0, 0, 0)
    return Insets(
      attributeSet.getLength(CSS.Attribute.MARGIN_TOP, styleSheet).toInt(),
      attributeSet.getLength(CSS.Attribute.MARGIN_LEFT, styleSheet).toInt(),
      attributeSet.getLength(CSS.Attribute.MARGIN_BOTTOM, styleSheet).toInt(),
      attributeSet.getLength(CSS.Attribute.MARGIN_RIGHT, styleSheet).toInt(),
    )
  }

@Suppress("UseDPIAwareInsets")
val View.cssBorderWidths: Insets
  get() {
    val styleSheet = (document as HTMLDocument).styleSheet
    val attributeSet = attributes ?: return Insets(0, 0, 0, 0)
    return Insets(
      attributeSet.getLength(CSS.Attribute.BORDER_TOP_WIDTH, styleSheet).toInt(),
      attributeSet.getLength(CSS.Attribute.BORDER_LEFT_WIDTH, styleSheet).toInt(),
      attributeSet.getLength(CSS.Attribute.BORDER_BOTTOM_WIDTH, styleSheet).toInt(),
      attributeSet.getLength(CSS.Attribute.BORDER_RIGHT_WIDTH, styleSheet).toInt(),
    )
  }

val View.cssBorderRadius: Float
  // "caption-side" is used as "border-radius"
  get() = attributes.getAttribute(CSS_ATTRIBUTE_CAPTION_SIDE)
            ?.asSafely<String>()
            ?.takeIf { it.endsWith("px") }
            ?.removeSuffix("px")
            ?.toFloatOrNull()
          ?: 0f

val View.cssBorderColors: BorderColors
  get() = BorderColors(
    attributes.getColor(CSS.Attribute.BORDER_TOP_COLOR),
    attributes.getColor(CSS.Attribute.BORDER_LEFT_COLOR),
    attributes.getColor(CSS.Attribute.BORDER_BOTTOM_COLOR),
    attributes.getColor(CSS.Attribute.BORDER_RIGHT_COLOR),
  )

val Insets.width: Int
  get() = right + left

val Insets.height: Int
  get() = top + bottom

internal val BoxView.minorRequest: SizeRequirements?
  get() = minorRequestField.get(this) as? SizeRequirements

internal val BlockView.painter: BoxPainter
  get() = blockViewPainterField.get(this) as BoxPainter

internal var BoxPainter.bg: Color?
  get() = boxPainterBgField.get(this) as? Color
  set(value) = boxPainterBgField.set(this, value)

internal fun GlyphView.getJustificationInfo(rowStartOffset: Int): JustificationInfo =
  getJustificationInfo.invoke(this, rowStartOffset).let {
    JustificationInfo(
      JustificationInfoClass.getDeclaredField("start").get(it) as Int,
      JustificationInfoClass.getDeclaredField("end").get(it) as Int,
      JustificationInfoClass.getDeclaredField("leadingSpaces").get(it) as Int,
      JustificationInfoClass.getDeclaredField("contentSpaces").get(it) as Int,
      JustificationInfoClass.getDeclaredField("trailingSpaces").get(it) as Int,
      JustificationInfoClass.getDeclaredField("hasTab").get(it) as Boolean,
      JustificationInfoClass.getDeclaredField("spaceMap").get(it) as BitSet,
    )
  }

internal fun paintControlBackgroundAndBorder(
  graphics: Graphics,
  rect: Rectangle,
  background: Color?,
  borderRadius: Float,
  margin: Insets,
  borderWidths: Insets?,
  borderColors: BorderColors?,
  noBorderOnTheLeft: Boolean = false,
  noBorderOnTheRight: Boolean = false,
) {
  val borderWidth = borderWidths?.let { minOf(it.top, it.left, it.bottom, it.right) } ?: 0
  val leftInset: Float = if (noBorderOnTheLeft) -borderRadius else margin.left + borderWidth / 2f
  val rightInset: Float = if (noBorderOnTheRight) -borderRadius else margin.right + borderWidth / 2f
  val borderShape = RoundRectangle2D.Float(rect.x + leftInset,
                                           (rect.y + margin.top).toFloat() + borderWidth / 2f,
                                           rect.width - leftInset - rightInset,
                                           (rect.height - margin.height).toFloat() - borderWidth,
                                           borderRadius, borderRadius)
  val g = graphics.create() as Graphics2D
  g.clip(rect)
  g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
  g.color = background
  g.fill(borderShape)
  val borderColor = borderColors?.let {
    sequenceOf(it.top, it.left, it.bottom, it.right)
      .reduceOrNull { c1, c2 -> if (c1 != c2) null else c1 }
  }
  if (borderWidth > 0 && borderColor != null) {
    g.color = borderColor
    g.stroke = BasicStroke(borderWidth.toFloat())
    g.draw(borderShape)
  }
}

private fun AttributeSet.getLength(attribute: CSS.Attribute, styleSheet: StyleSheet): Float =
  cssLengthMethod.invoke(css, this, attribute, styleSheet) as Float

private fun AttributeSet.getColor(attribute: CSS.Attribute): Color? =
  cssGetColorMethod.invoke(css, this, attribute) as Color?


private val css: CSS by lazy(LazyThreadSafetyMode.PUBLICATION) { CSS() }

private val cssLengthMethod by lazy(LazyThreadSafetyMode.PUBLICATION) {
  CSS::class.java.getDeclaredMethod("getLength", AttributeSet::class.java, CSS.Attribute::class.java, StyleSheet::class.java)
    .also { it.isAccessible = true }
}

private val cssGetColorMethod by lazy(LazyThreadSafetyMode.PUBLICATION) {
  CSS::class.java.getDeclaredMethod("getColor", AttributeSet::class.java, CSS.Attribute::class.java)
    .also { it.isAccessible = true }
}

private val getJustificationInfo by lazy(LazyThreadSafetyMode.PUBLICATION) {
  GlyphView::class.java.getDeclaredMethod("getJustificationInfo", Int::class.javaPrimitiveType)
    .also { it.isAccessible = true }
}

private val minorRequestField by lazy(LazyThreadSafetyMode.PUBLICATION) {
  BoxView::class.java.getDeclaredField("minorRequest")
    .also { it.isAccessible = true }
}

private val blockViewPainterField by lazy(LazyThreadSafetyMode.PUBLICATION) {
  BlockView::class.java.getDeclaredField("painter")
    .also { it.isAccessible = true }
}

private val boxPainterBgField by lazy(LazyThreadSafetyMode.PUBLICATION) {
  BoxPainter::class.java.getDeclaredField("bg")
    .also { it.isAccessible = true }
}

private val JustificationInfoClass by lazy(LazyThreadSafetyMode.PUBLICATION) {
  GlyphView::class.java.declaredClasses
    .find { it.simpleName == "JustificationInfo" }!!
}

internal data class JustificationInfo(
  val start: Int,
  val end: Int,
  val leadingSpaces: Int,
  val contentSpaces: Int,
  val trailingSpaces: Int,
  val hasTab: Boolean,
  val spaceMap: BitSet,
)

data class BorderColors(
  val top: Color?,
  val left: Color?,
  val bottom: Color?,
  val right: Color?,
)