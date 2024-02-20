// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui.html

import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import java.util.*
import javax.swing.SizeRequirements
import javax.swing.text.AttributeSet
import javax.swing.text.BoxView
import javax.swing.text.GlyphView
import javax.swing.text.View
import javax.swing.text.html.CSS
import javax.swing.text.html.HTMLDocument
import javax.swing.text.html.StyleSheet

val View.cssPadding: JBInsets
  get() {
    val styleSheet = (document as HTMLDocument).styleSheet
    val attributeSet = attributes ?: return JBInsets.emptyInsets()
    return JBUI.insets(
      attributeSet.getLength(CSS.Attribute.PADDING_TOP, styleSheet).toInt(),
      attributeSet.getLength(CSS.Attribute.PADDING_LEFT, styleSheet).toInt(),
      attributeSet.getLength(CSS.Attribute.PADDING_BOTTOM, styleSheet).toInt(),
      attributeSet.getLength(CSS.Attribute.PADDING_RIGHT, styleSheet).toInt(),
    )
  }

val View.cssMargin: JBInsets
  get() {
    val styleSheet = (document as HTMLDocument).styleSheet
    val attributeSet = attributes ?: return JBInsets.emptyInsets()
    return JBUI.insets(
      attributeSet.getLength(CSS.Attribute.MARGIN_TOP, styleSheet).toInt(),
      attributeSet.getLength(CSS.Attribute.MARGIN_LEFT, styleSheet).toInt(),
      attributeSet.getLength(CSS.Attribute.MARGIN_BOTTOM, styleSheet).toInt(),
      attributeSet.getLength(CSS.Attribute.MARGIN_RIGHT, styleSheet).toInt(),
    )
  }

internal val BoxView.minorRequest: SizeRequirements?
  get() = minorRequestField.get(this) as? SizeRequirements

internal fun GlyphView.getJustificationInfo(rowStartOffset: Int): JustificationInfo =
  getJustificationInfo.invoke(this).let {
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

private fun AttributeSet.getLength(attribute: CSS.Attribute, styleSheet: StyleSheet): Float =
  cssLengthMethod.invoke(css, this, attribute, styleSheet) as Float

private val css: CSS by lazy(LazyThreadSafetyMode.PUBLICATION) { CSS() }

private val cssLengthMethod by lazy(LazyThreadSafetyMode.PUBLICATION) {
  CSS::class.java.getDeclaredMethod("getLength", AttributeSet::class.java, CSS.Attribute::class.java, StyleSheet::class.java)
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