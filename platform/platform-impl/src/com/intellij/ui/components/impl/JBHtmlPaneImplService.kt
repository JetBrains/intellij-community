// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.components.impl

import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.impl.EditorCssFontResolver
import com.intellij.ui.components.JBHtmlPane
import com.intellij.ui.components.JBHtmlPaneStyleConfiguration
import com.intellij.util.ui.CSSFontResolver
import org.jetbrains.annotations.Nls
import java.awt.Color
import javax.swing.text.html.StyleSheet

internal class JBHtmlPaneImplService: JBHtmlPane.ImplService {

  override fun transpileHtmlPaneInput(@Nls text: String): @Nls String =
    JBHtmlPaneInputTranspiler.transpileHtmlPaneInput(text)

  override fun defaultEditorCssFontResolver(): CSSFontResolver =
    EditorCssFontResolver.getGlobalInstance()

  override fun getDefaultStyleSheet(paneBackgroundColor: Color, configuration: JBHtmlPaneStyleConfiguration): StyleSheet =
    JBHtmlPaneStyleSheetRulesProvider.getStyleSheet(paneBackgroundColor, configuration)

  override fun getEditorColorsSchemeStyleSheet(editorColorsScheme: EditorColorsScheme): StyleSheet =
    EditorColorsSchemeStyleSheet(editorColorsScheme)

}