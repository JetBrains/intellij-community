// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui

import com.intellij.util.ui.ExtendableHTMLViewFactory.Companion.DEFAULT_EXTENSIONS
import javax.swing.text.Element
import javax.swing.text.View
import javax.swing.text.ViewFactory
import javax.swing.text.html.HTMLEditorKit
import javax.swing.text.html.StyleSheet

/**
 * Convenient way to create [HTMLEditorKit] to be used in [javax.swing.JEditorPane] and similar
 */
class HTMLEditorKitBuilder {
  private var viewFactory: ViewFactory = ExtendableHTMLViewFactory.DEFAULT
  private var overriddenRootStyle: StyleSheet? = null
  private var needGapsBetweenParagraphs = false
  private var loadCssFromFile = true
  private var fontResolver: CSSFontResolver? = null

  /**
   * Allows replacing default [ExtendableHTMLViewFactory] extensions
   */
  fun replaceViewFactoryExtensions(vararg extensions: (Element, View) -> View?) = apply {
    viewFactory = ExtendableHTMLViewFactory(extensions.asList())
  }

  /**
   * Allows reconfiguring default [ExtendableHTMLViewFactory] extensions
   *
   * [extensions] take priority over default extensions, i.e. when [ExtendableHTMLViewFactory.Extensions.icons] extension is added
   * it will be invoked before the default icons extension
   */
  fun withViewFactoryExtensions(vararg extensions: (Element, View) -> View?) = apply {
    viewFactory = ExtendableHTMLViewFactory(extensions.toList() + DEFAULT_EXTENSIONS)
  }

  /**
   * Optimized shorthand for [withViewFactoryExtensions] with [ExtendableHTMLViewFactory.Extensions.WORD_WRAP] extension
   */
  fun withWordWrapViewFactory() = apply {
    viewFactory = ExtendableHTMLViewFactory.DEFAULT_WORD_WRAP
  }

  /**
   * This stylesheet will be used by default in all documents created via [HTMLEditorKit.createDefaultDocument]
   * Generally - a default stylesheet for [javax.swing.JEditorPane] unless you override the document manually
   *
   * By default [StyleSheetUtil.getDefaultStyleSheet] is used
   */
  fun withStyleSheet(styleSheet: StyleSheet) = apply {
    overriddenRootStyle = styleSheet
  }

  /**
   * By default, we add [StyleSheetUtil.NO_GAPS_BETWEEN_PARAGRAPHS_STYLE] style to the default style
   * This allows to prevent that
   *
   * Does not affect custom stylesheet set by [withStyleSheet]
   */
  fun withGapsBetweenParagraphs() = apply {
    needGapsBetweenParagraphs = true
  }

  /**
   * By default, document created by [HTMLEditorKit.createDefaultDocument] applies all styles from [<style>] tags in the document
   * Sometimes we don't want to do that
   */
  fun withoutContentCss() = apply {
    loadCssFromFile = false
  }

  /**
   * Provides the way to use dynamic fonts in the document by designating some placeholder
   * Most prominent example is editor font [com.intellij.openapi.editor.impl.EditorCssFontResolver]
   */
  fun withFontResolver(resolver: CSSFontResolver) = apply {
    fontResolver = resolver
  }

  fun build(): HTMLEditorKit {
    val styleSheet = overriddenRootStyle ?: createHtmlStyleSheet()
    return JBHtmlEditorKit(viewFactory, styleSheet, !loadCssFromFile).apply {
      if (fontResolver != null) setFontResolver(fontResolver)
    }
  }

  private fun createHtmlStyleSheet(): StyleSheet {
    val sheet = StyleSheetUtil.getDefaultStyleSheet()
    if (!needGapsBetweenParagraphs) {
      sheet.addStyleSheet(StyleSheetUtil.NO_GAPS_BETWEEN_PARAGRAPHS_STYLE)
    }
    return sheet
  }

  companion object {
    /**
     * Create a simple editor kit with default values
     */
    @JvmStatic
    fun simple() = HTMLEditorKitBuilder().build()
  }
}