// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.ui.visualizedtext.common

import com.intellij.openapi.Disposable
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.components.JBScrollPane
import com.intellij.xdebugger.XDebuggerBundle
import com.intellij.xdebugger.impl.ui.visualizedtext.TextVisualizerContentType
import com.intellij.xdebugger.impl.ui.visualizedtext.VisualizedContentTabWithStats
import com.intellij.xdebugger.ui.TextValueVisualizer
import com.intellij.xdebugger.ui.VisualizedContentTab
import javax.swing.JComponent
import javax.swing.JEditorPane

internal class HtmlTextVisualizer : TextValueVisualizer {

  override fun visualize(value: @NlsSafe String): List<VisualizedContentTab> {
    if (!isHtml(value)) {
      return emptyList()
    }

    return listOf(object : VisualizedContentTab, VisualizedContentTabWithStats {
      override val name
        get() = XDebuggerBundle.message("xdebugger.visualized.text.name.html")
      override val id
        get() = HtmlTextVisualizer::class.qualifiedName!!
      override val contentTypeForStats
        get() = TextVisualizerContentType.HTML

      override fun createComponent(project: Project, parentDisposable: Disposable): JComponent {
        val editor = JEditorPane()
        editor.isEditable = false
        editor.contentType = "text/html"
        editor.text = value
        return JBScrollPane(editor)
      }
    })
  }

  override fun detectFileType(value: @NlsSafe String): FileType? =
    if (isHtml(value)) htmlFileType else null

  private fun isHtml(value: @NlsSafe String): Boolean =
    // Try to somehow verify that the input resembles HTML: contains tags and starts with '<'.
    value.contains(Helper.htmlTagsRegex) && value.firstOrNull { !it.isWhitespace() } == '<'

  private val htmlFileType
    get() =
      // Right now we don't want to have an explicit static dependency here.
      // In an ideal world, this class would be part of the optional module of the debugger plugin with a dependency on intellij.xml.psi.impl.
      FileTypeManager.getInstance().getStdFileType("HTML")
}

private object Helper {

  val htmlTags = setOf("a", "abbr", "acronym", "address", "applet", "area", "article", "aside", "audio", "b", "base", "basefont",
                       "bdi", "bdo", "big", "blockquote", "body", "br", "button", "canvas", "caption", "center", "cite", "code",
                       "col", "colgroup", "data", "datalist", "dd", "del", "details", "dfn", "dialog", "dir", "div", "dl", "dt",
                       "em", "embed", "fieldset", "figcaption", "figure", "font", "footer", "form", "frame", "frameset",
                       "h1", "h2", "h3", "h4", "h5", "h6",
                       "head", "header", "hr", "html", "i", "iframe", "img", "input", "ins", "kbd", "label", "legend", "li",
                       "link", "main", "map", "mark", "meta", "meter", "nav", "noframes", "noscript", "object", "ol", "optgroup",
                       "option", "output", "p", "param", "picture", "pre", "progress", "q", "rp", "rt", "ruby", "s", "samp",
                       "script", "section", "select", "small", "source", "span", "strike", "strong", "style", "sub", "summary",
                       "sup", "svg", "table", "tbody", "td", "template", "textarea", "tfoot", "th", "thead", "time", "title",
                       "tr", "track", "tt", "u", "ul", "var", "video", "wbr")

  val htmlTagsRegex = Regex("<(${htmlTags.joinToString("|")})[ >/]")
}
