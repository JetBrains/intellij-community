// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui.html

import com.intellij.util.asSafely
import com.intellij.util.ui.JBHtmlEditorKit
import com.intellij.util.ui.html.CssAttributesEx.BORDER_RADIUS
import org.jetbrains.annotations.ApiStatus
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Insets
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.geom.RoundRectangle2D
import java.util.BitSet
import java.util.Hashtable
import javax.swing.SizeRequirements
import javax.swing.text.AttributeSet
import javax.swing.text.BoxView
import javax.swing.text.Element
import javax.swing.text.FlowView
import javax.swing.text.GlyphView
import javax.swing.text.LabelView
import javax.swing.text.StyleContext
import javax.swing.text.View
import javax.swing.text.View.X_AXIS
import javax.swing.text.html.BlockView
import javax.swing.text.html.CSS
import javax.swing.text.html.HTML
import javax.swing.text.html.StyleSheet
import javax.swing.text.html.StyleSheet.BoxPainter
import kotlin.math.max

internal val HTML_Tag_SUMMARY: HTML.Tag get() = SUMMARY_TAG
internal val HTML_Tag_DETAILS: HTML.Tag get() = DETAILS_TAG

internal val HTML_Tag_CUSTOM_BLOCK_TAGS: Set<HTML.Tag> get() = CUSTOM_BLOCK_TAGS_SET

@Suppress("UseDPIAwareInsets")
val View.cssPadding: Insets
  get() {
    val styleSheet = (document as JBHtmlEditorKit.JBHtmlDocument).styleSheet
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
    val styleSheet = (document as JBHtmlEditorKit.JBHtmlDocument).styleSheet
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
    val styleSheet = (document as JBHtmlEditorKit.JBHtmlDocument).styleSheet
    val attributeSet = attributes ?: return Insets(0, 0, 0, 0)
    return Insets(
      attributeSet.getLength(CSS.Attribute.BORDER_TOP_WIDTH, styleSheet).toInt(),
      attributeSet.getLength(CSS.Attribute.BORDER_LEFT_WIDTH, styleSheet).toInt(),
      attributeSet.getLength(CSS.Attribute.BORDER_BOTTOM_WIDTH, styleSheet).toInt(),
      attributeSet.getLength(CSS.Attribute.BORDER_RIGHT_WIDTH, styleSheet).toInt(),
    )
  }

val View.cssBorderRadius: Float
  get() = attributes.getAttribute(BORDER_RADIUS)
            ?.asSafely<String>()
            ?.removeSuffix("px")
            ?.toFloatOrNull()
          ?: 0f

val View.cssBorderColors: BorderColors
  get() {
    val styleSheet = (document as JBHtmlEditorKit.JBHtmlDocument).styleSheet
    return BorderColors(
      attributes.getColor( CSS.Attribute.BORDER_TOP_COLOR, styleSheet),
      attributes.getColor( CSS.Attribute.BORDER_LEFT_COLOR, styleSheet),
      attributes.getColor(CSS.Attribute.BORDER_BOTTOM_COLOR, styleSheet),
      attributes.getColor(CSS.Attribute.BORDER_RIGHT_COLOR, styleSheet),
    )
  }

val Insets.width: Int
  get() = right + left

val Insets.height: Int
  get() = top + bottom

@ApiStatus.Internal
fun visitViews(view: View, visitor: (View) -> Unit) {
  for (i in 0..<view.viewCount) {
    view.getView(i)?.let {
      visitViews(it, visitor)
    }
  }
  visitor(view)
}

@ApiStatus.Internal
fun View.reapplyCss() {
  visitViews(this) { childView ->
    when (childView) {
      is BlockView -> blockViewSetPropertiesFromAttributesMethod.invoke(childView)
      is LabelView -> labelViewSetPropertiesFromAttributesMethod.invoke(childView)
    }
  }
}

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
  val borderColor = borderColors?.let {
    sequenceOf(it.top, it.left, it.bottom, it.right)
      .reduceOrNull { c1, c2 -> if (c1 != c2) null else c1 }
  }
  val borderWidth = borderWidths?.let { minOf(it.top, it.left, it.bottom, it.right) } ?: 0
  if (background == null && (borderWidth <= 0 || borderColor == null))
    return
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
  if (background != null) {
    g.color = background
    g.fill(borderShape)
  }
  if (borderWidth > 0 && borderColor != null) {
    g.color = borderColor
    g.stroke = BasicStroke(borderWidth.toFloat())
    g.draw(borderShape)
  }
  g.dispose()
}

private fun AttributeSet.getLength(attribute: CSS.Attribute, styleSheet: StyleSheet): Float =
  cssLengthMethod.invoke(styleSheet.css, this, attribute, styleSheet) as Float

private fun AttributeSet.getColor(attribute: CSS.Attribute, styleSheet: StyleSheet): Color? =
  cssGetColorMethod.invoke(styleSheet.css, this, attribute) as Color?

private val CUSTOM_BLOCK_TAGS_SET = mutableSetOf<HTML.Tag>()

private fun createNewHtmlBlockTag(name: String): HTML.Tag {
  val newTag = TagConstructor.newInstance(name, true, true) as HTML.Tag
  HTMLTagHashtable.put(name, newTag)
  StyleContext.registerStaticAttributeKey(newTag)
  CUSTOM_BLOCK_TAGS_SET.add(newTag)
  return newTag
}

internal fun Element.getIntAttr(attribute: HTML.Attribute, defaultValue: Int): Int {
  val attr = attributes
  if (!attr.isDefined(attribute)) return defaultValue

  val value = attr.getAttribute(attribute) as? String ?: return defaultValue
  return try {
    max(0, value.toInt())
  }
  catch (_: NumberFormatException) {
    defaultValue
  }
}

internal class ImageViewPreferredSpan(
  private val view: View,
  private val getBaseSpan: (Int) -> Float,
) {
  private var myAvailableWidth = 0

  fun get(axis: Int): Float {
    val baseSpan = getBaseSpan(axis)
    if (axis == X_AXIS) {
      return baseSpan
    }
    else {
      var availableWidth = view.availableWidth
      if (availableWidth <= 0) return baseSpan
      val baseXSpan = getBaseSpan(X_AXIS)
      if (baseXSpan <= 0) return baseSpan
      if (availableWidth > baseXSpan) {
        availableWidth = baseXSpan.toInt()
      }
      if (myAvailableWidth > 0 && availableWidth != myAvailableWidth) {
        view.preferenceChanged(null, false, true)
      }
      myAvailableWidth = availableWidth
      return baseSpan * availableWidth / baseXSpan
    }
  }
}


private val View.availableWidth: Int
  get() {
    var v: View? = this
    while (v != null) {
      val parent = v.parent
      if (parent is FlowView) {
        val childCount = parent.viewCount
        for (i in 0 until childCount) {
          if (parent.getView(i) === v) {
            return parent.getFlowSpan(i)
          }
        }
      }
      v = parent
    }
    return 0
  }

internal val StyleSheet.css: CSS
  get() = styleSheetCssField.get(this) as CSS

internal fun StyleSheet.patchAttributes(): StyleSheet {
  css.patchAttributes()
  return this
}

private fun CSS.patchAttributes(): CSS {
  @Suppress("UNCHECKED_CAST")
  val valueConvertor = cssValueConvertorField.get(this) as Hashtable<Any, Any>
  val cssValue = cssCssValueConstructor.newInstance()
  cssAttributesExList.forEach {
    valueConvertor[it] = cssValue
  }
  return this
}

internal fun createCssAttribute(name: String, defaultValue: String?, inherited: Boolean): CSS.Attribute {
  val attribute = cssAttributeConstructor.newInstance(name, defaultValue, inherited) as CSS.Attribute

  @Suppress("UNCHECKED_CAST")
  val attributeMap = cssAttributeMapField.get(null) as Hashtable<String, CSS.Attribute>
  attributeMap[name] = attribute
  return attribute
}

private val cssAttributesExList by lazy(LazyThreadSafetyMode.PUBLICATION) {
  CssAttributesEx::class.java.fields
    .mapNotNull { it.get(null) as? CSS.Attribute }
}

private val cssLengthMethod by lazy(LazyThreadSafetyMode.PUBLICATION) {
  CSS::class.java.getDeclaredMethod("getLength", AttributeSet::class.java, CSS.Attribute::class.java, StyleSheet::class.java)
    .also { it.isAccessible = true }
}

private val cssGetColorMethod by lazy(LazyThreadSafetyMode.PUBLICATION) {
  CSS::class.java.getDeclaredMethod("getColor", AttributeSet::class.java, CSS.Attribute::class.java)
    .also { it.isAccessible = true }
}

private val cssAttributeMapField by lazy(LazyThreadSafetyMode.PUBLICATION) {
  CSS::class.java.getDeclaredField("attributeMap")
    .also { it.isAccessible = true }
}

private val cssValueConvertorField by lazy(LazyThreadSafetyMode.PUBLICATION) {
  CSS::class.java.getDeclaredField("valueConvertor")
    .also { it.isAccessible = true }
}

private val cssAttributeConstructor by lazy(LazyThreadSafetyMode.PUBLICATION) {
  CSS.Attribute::class.java.getDeclaredConstructor(String::class.java, String::class.java, Boolean::class.javaPrimitiveType)
    .also { it.isAccessible = true }
}

private val cssCssValueConstructor by lazy(LazyThreadSafetyMode.PUBLICATION) {
  CSS::class.java.declaredClasses.find { it.simpleName == "CssValue" }!!
    .getDeclaredConstructor()
    .also { it.isAccessible = true }
}

private val styleSheetCssField by lazy(LazyThreadSafetyMode.PUBLICATION) {
  StyleSheet::class.java.getDeclaredField("css")
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

private val TagConstructor by lazy(LazyThreadSafetyMode.PUBLICATION) {
  HTML.Tag::class.java.getDeclaredConstructor(String::class.java, Boolean::class.javaPrimitiveType, Boolean::class.javaPrimitiveType)
    .also { it.isAccessible = true }
}

@Suppress("UNCHECKED_CAST")
private val HTMLTagHashtable by lazy(LazyThreadSafetyMode.PUBLICATION) {
  HTML::class.java.getDeclaredField("tagHashtable")
    .also { it.isAccessible = true }.get(null) as Hashtable<String, HTML.Tag>
}

private val blockViewSetPropertiesFromAttributesMethod by lazy {
  BlockView::class.java.getDeclaredMethod("setPropertiesFromAttributes").also {
    it.isAccessible = true
  }
}

private val labelViewSetPropertiesFromAttributesMethod by lazy {
  LabelView::class.java.getDeclaredMethod("setPropertiesFromAttributes").also {
    it.isAccessible = true
  }
}

private val SUMMARY_TAG = createNewHtmlBlockTag("summary")
private val DETAILS_TAG = createNewHtmlBlockTag("details")

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