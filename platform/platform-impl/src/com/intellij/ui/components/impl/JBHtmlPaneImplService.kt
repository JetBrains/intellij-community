// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.components.impl

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.impl.EditorCssFontResolver
import com.intellij.ui.components.JBHtmlPane
import com.intellij.ui.components.JBHtmlPaneStyleConfiguration
import com.intellij.util.ui.CSSFontResolver
import com.intellij.util.ui.html.reapplyCss
import com.intellij.util.ui.html.visitViews
import org.jetbrains.annotations.Nls
import java.awt.Color
import java.awt.Component
import java.awt.Image
import java.net.URL
import java.util.*
import javax.swing.JComponent
import javax.swing.text.ComponentView
import javax.swing.text.LabelView
import javax.swing.text.View
import javax.swing.text.html.BlockView
import javax.swing.text.html.StyleSheet

internal class JBHtmlPaneImplService : JBHtmlPane.ImplService {

  override fun transpileHtmlPaneInput(@Nls text: String): @Nls String =
    JBHtmlPaneInputTranspiler.transpileHtmlPaneInput(text)

  override fun defaultEditorCssFontResolver(): CSSFontResolver =
    EditorCssFontResolver.getGlobalInstance()

  override fun getDefaultStyleSheet(paneBackgroundColor: Color, scaleFactor: Float, baseFontSize: Int, configuration: JBHtmlPaneStyleConfiguration): StyleSheet =
    ApplicationManager.getApplication().service<JBHtmlPaneStyleSheetRulesProvider>().getStyleSheet(paneBackgroundColor, scaleFactor, baseFontSize, configuration)

  override fun getEditorColorsSchemeStyleSheet(editorColorsScheme: EditorColorsScheme): StyleSheet =
    EditorColorsSchemeStyleSheet(editorColorsScheme)

  override fun createDefaultImageResolver(pane: JBHtmlPane): Dictionary<URL, Image> =
    JBHtmlPaneImageResolver(pane, null)

  override fun applyCssToView(pane: JBHtmlPane) {
    pane.ui.getRootView(pane).reapplyCss()
  }

  override fun ensureEditableViewsAreNotFocusable(pane: JBHtmlPane) {
    visitViews(pane.ui.getRootView(pane)) { childView ->
      when (childView) {
        is ComponentView ->
          if (childView.javaClass.name.let {
              it.startsWith("javax.swing.text.html.")
              && (it.endsWith("html.EditableView") || it.endsWith("html.HiddenTagView") || it.endsWith("html.CommentView"))
            }) {
            val components = mutableListOf<Component?>(childView.component)
            while (components.isNotEmpty()) {
              val component = components.removeLast()
              component?.isFocusable = false
              if (component is JComponent)
                components.addAll(component.components)
            }
          }
      }
    }
  }

}