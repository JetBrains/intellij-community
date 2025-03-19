// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.components.impl

import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.markup.EffectType
import com.intellij.ui.components.JBHtmlPaneStyleConfiguration.Companion.editorColorClassPrefix
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.html.CssAttributesEx
import java.awt.Color
import java.awt.Font
import javax.swing.text.Element
import javax.swing.text.Style
import javax.swing.text.StyleConstants
import javax.swing.text.html.CSS
import javax.swing.text.html.HTML
import javax.swing.text.html.StyleSheet

internal class EditorColorsSchemeStyleSheet(private val editorColorsScheme: EditorColorsScheme) : StyleSheet() {

  private val computedStyles = mutableMapOf<String, Style>()

  override fun getRule(selector: String): Style? {
    val ind = selector.indexOf(".$editorColorClassPrefix")
    if (ind >= 0) {
      val token = selector.substring(ind + editorColorClassPrefix.length + 1).takeWhile { !it.isWhitespace() }
      return getTokenRule(token)
    }
    return null
  }

  override fun getRule(t: HTML.Tag, e: Element): Style? {
    val cls = e.attributes.getAttribute(HTML.Attribute.CLASS) as? String
    if (cls?.startsWith(editorColorClassPrefix) == true) {
      return getTokenRule(cls.substring(editorColorClassPrefix.length))
    }
    return null
  }

  private fun getTokenRule(token: String): Style =
    computedStyles.computeIfAbsent(token) { createTokenRule(it) }

  private fun createTokenRule(token: String): Style {
    val attributes = editorColorsScheme.getAttributes(TextAttributesKey.createTextAttributesKey(token), true)
    val result = NamedStyle(token, null)
    attributes.foregroundColor?.let { result.addAttribute(CSS.Attribute.COLOR, colorValue(it)) }
    attributes.backgroundColor?.let { result.addAttribute(CSS.Attribute.BACKGROUND_COLOR, colorValue(it)) }

    if (attributes.fontType and Font.BOLD != 0)
      result.addAttribute(StyleConstants.Bold, true)
    if (attributes.fontType and Font.ITALIC != 0)
      result.addAttribute(StyleConstants.Italic, true)

    val effectType = attributes.effectType
    if (attributes.hasEffects() && effectType != null) {
      when (effectType) {
        EffectType.LINE_UNDERSCORE,
        EffectType.WAVE_UNDERSCORE,
        EffectType.BOLD_LINE_UNDERSCORE,
        EffectType.BOLD_DOTTED_LINE ->
          result.addAttribute(StyleConstants.Underline, true)
        EffectType.STRIKEOUT ->
          result.addAttribute(StyleConstants.StrikeThrough, true)
        EffectType.BOXED, EffectType.SLIGHTLY_WIDER_BOX, EffectType.SEARCH_MATCH, EffectType.ROUNDED_BOX -> {
          attributes.effectColor?.let { effectColor ->
            val borderWidth = lengthValue(JBUI.scale(1).toFloat())
            result.addAttribute(CSS.Attribute.BORDER_TOP_WIDTH, borderWidth)
            result.addAttribute(CSS.Attribute.BORDER_RIGHT_WIDTH, borderWidth)
            result.addAttribute(CSS.Attribute.BORDER_BOTTOM_WIDTH, borderWidth)
            result.addAttribute(CSS.Attribute.BORDER_LEFT_WIDTH, borderWidth)
            result.addAttribute(CSS.Attribute.MARGIN_TOP, borderWidth)
            result.addAttribute(CSS.Attribute.MARGIN_RIGHT, borderWidth)
            result.addAttribute(CSS.Attribute.MARGIN_BOTTOM, borderWidth)
            result.addAttribute(CSS.Attribute.MARGIN_LEFT, borderWidth)

            val color = colorValue(effectColor)
            result.addAttribute(CSS.Attribute.BORDER_TOP_COLOR, color)
            result.addAttribute(CSS.Attribute.BORDER_RIGHT_COLOR, color)
            result.addAttribute(CSS.Attribute.BORDER_BOTTOM_COLOR, color)
            result.addAttribute(CSS.Attribute.BORDER_LEFT_COLOR, color)

            val style = cssValueSolid
            result.addAttribute(CSS.Attribute.BORDER_TOP_STYLE, style)
            result.addAttribute(CSS.Attribute.BORDER_RIGHT_STYLE, style)
            result.addAttribute(CSS.Attribute.BORDER_BOTTOM_STYLE, style)
            result.addAttribute(CSS.Attribute.BORDER_LEFT_STYLE, style)

            val paddingTopBottom = lengthValue(JBUI.scale(2).toFloat())
            result.addAttribute(CSS.Attribute.PADDING_TOP, paddingTopBottom)
            result.addAttribute(CSS.Attribute.PADDING_BOTTOM, paddingTopBottom)

            if (effectType == EffectType.SLIGHTLY_WIDER_BOX) {
              val paddingLeftRight = lengthValue(JBUI.scale(2).toFloat())
              result.addAttribute(CSS.Attribute.PADDING_LEFT, paddingLeftRight)
              result.addAttribute(CSS.Attribute.PADDING_RIGHT, paddingLeftRight)
            }

            if (effectType == EffectType.ROUNDED_BOX) {
              result.addAttribute(CssAttributesEx.BORDER_RADIUS, "${JBUI.scale(2)}px")
            } else {
              // force usage of InlineViewEx
              result.addAttribute(CssAttributesEx.BORDER_RADIUS, "0px")
            }
          }
        }
        EffectType.FADED -> {
          // Do nothing
        }
      }
    }
    return result
  }
  companion object {

    private fun colorValue(color: Color): Any =
      ColorValueConstructor.newInstance()
        .also { ColorValueColorField.set(it, color) }

    private fun lengthValue(value: Float): Any =
      LengthValueConstructor.newInstance()
        .also { LengthValueSpanField.set(it, value) }

    private val CssValueClass = CSS::class.java.declaredClasses
      .find { it.simpleName == "Value" }!!
    private val cssValueSolid = CssValueClass.getDeclaredField("SOLID")
      .also { it.isAccessible = true }
      .get(null)

    private val CssCssValueClass = CSS::class.java.declaredClasses
      .find { it.simpleName == "CssValue" }!!
    private val CssCssValueSvalueField = CssCssValueClass.getDeclaredField("svalue")
      .also { it.isAccessible = true }

    private val ColorValueClass = CSS::class.java.declaredClasses
      .find { it.simpleName == "ColorValue" }!!
    private val ColorValueConstructor = ColorValueClass.getDeclaredConstructor()
      .also { it.isAccessible = true }
    private val ColorValueColorField = ColorValueClass.getDeclaredField("c")
      .also { it.isAccessible = true }

    private val StringValueClass = CSS::class.java.declaredClasses
      .find { it.simpleName == "StringValue" }!!
    private val StringValueConstructor = StringValueClass.getDeclaredConstructor()
      .also { it.isAccessible = true }

    private val LengthValueClass = CSS::class.java.declaredClasses
      .find { it.simpleName == "LengthValue" }!!
    private val LengthValueConstructor = LengthValueClass.getDeclaredConstructor()
      .also { it.isAccessible = true }
    private val LengthValueSpanField = LengthValueClass.getDeclaredField("span")
      .also { it.isAccessible = true }

  }
}