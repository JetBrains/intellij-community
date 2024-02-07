// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui.html

import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import javax.swing.text.AttributeSet
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

private fun AttributeSet.getLength(attribute: CSS.Attribute, styleSheet: StyleSheet): Float =
  cssLength.invoke(css, this, attribute, styleSheet) as Float

private val css: CSS by lazy(LazyThreadSafetyMode.PUBLICATION) { CSS() }
private val cssLength by lazy(LazyThreadSafetyMode.PUBLICATION) {
  CSS::class.java.getDeclaredMethod("getLength", AttributeSet::class.java, CSS.Attribute::class.java, StyleSheet::class.java)
    .also { it.isAccessible = true }
}