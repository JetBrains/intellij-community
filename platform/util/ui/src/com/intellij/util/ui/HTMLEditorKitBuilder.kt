// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui

import javax.swing.text.ViewFactory
import javax.swing.text.html.HTMLEditorKit
import javax.swing.text.html.StyleSheet

/**
 * Convenient way to create [HTMLEditorKit] to be used in [javax.swing.JEditorPane] and similar
 */
class HTMLEditorKitBuilder {

  private var viewFactory = UIUtil.DEFAULT_HTML_VIEW_FACTORY
  private var overriddenRootStyle: StyleSheet? = null
  private var needGapsBetweenParagraphs = false
  private var loadCssFromFile = true
  private var fontResolver: CSSFontResolver? = null

  /**
   * Sets a [HTMLEditorKit.getViewFactory]
   * Allows to override the way views are constructed from HTML
   *
   * By default [UIUtil.DEFAULT_HTML_VIEW_FACTORY] is used
   */
  fun withViewFactory(factory: ViewFactory) = apply {
    viewFactory = factory
  }

  //TODO: remove when there's a configurable factory
  fun withWordWrapViewFactory() = apply {
    viewFactory = UIUtil.JBWordWrapHtmlEditorKit.ourFactory
  }

  /**
   * This stylesheet will be used by default in all documents created via [HTMLEditorKit.createDefaultDocument]
   * Generally - a default stylesheet for [javax.swing.JEditorPane] unless you override the document manually
   *
   * By default [StyleSheetUtil.createJBDefaultStyleSheet] is used
   */
  fun withStyleSheet(styleSheet: StyleSheet) = apply {
    overriddenRootStyle = styleSheet
  }

  /**
   * By default, we add [UIUtil.NO_GAPS_BETWEEN_PARAGRAPHS_STYLE] style to the default style
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
    val sheet = StyleSheetUtil.createJBDefaultStyleSheet()
    if (!needGapsBetweenParagraphs) sheet.addStyleSheet(UIUtil.NO_GAPS_BETWEEN_PARAGRAPHS_STYLE)
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