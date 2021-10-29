// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.dsl.builder.components

import com.intellij.openapi.ui.panel.ComponentPanelBuilder
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.ui.ColorUtil
import com.intellij.ui.dsl.UiDslException
import com.intellij.ui.dsl.builder.HyperlinkEventAction
import com.intellij.ui.dsl.builder.MAX_LINE_LENGTH_NO_WRAP
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import javax.swing.JEditorPane
import javax.swing.event.HyperlinkEvent

private val DENIED_TAGS = listOf("<html>", "<body>")

@ApiStatus.Internal
internal enum class DslLabelType {
  LABEL,
  COMMENT
}

@ApiStatus.Internal
internal class DslLabel(private val type: DslLabelType) : JEditorPane() {

  var action: HyperlinkEventAction? = null

  init {
    contentType = UIUtil.HTML_MIME
    editorKit = UIUtil.getHTMLEditorKit()

    foreground = when (type) {
      DslLabelType.COMMENT -> JBUI.CurrentTheme.ContextHelp.FOREGROUND
      DslLabelType.LABEL -> JBUI.CurrentTheme.Label.foreground()
    }

    addHyperlinkListener { e ->
      when (e?.eventType) {
        HyperlinkEvent.EventType.ACTIVATED -> action?.hyperlinkActivated(e)
        HyperlinkEvent.EventType.ENTERED -> action?.hyperlinkEntered(e)
        HyperlinkEvent.EventType.EXITED -> action?.hyperlinkExited(e)
      }
    }

    patchFont()
  }

  override fun updateUI() {
    super.updateUI()

    isFocusable = false
    isEditable = false
    border = null
    background = UIUtil.TRANSPARENT_COLOR
    isOpaque = false
    patchFont()
  }

  override fun getBaseline(width: Int, height: Int): Int {
    // JEditorPane doesn't support baseline, calculate it manually from font
    val fontMetrics = getFontMetrics(font)
    return fontMetrics.ascent
  }

  fun setHtmlText(@Nls text: String, maxLineLength: Int) {
    for (deniedTag in DENIED_TAGS) {
      if (text.contains(deniedTag)) {
        throw UiDslException("Text contains denied tag $deniedTag: $text")
      }
    }

    @Suppress("HardCodedStringLiteral")
    var processedText = HtmlChunk.raw(text.replace("<a>", "<a href=''>", ignoreCase = true))

    if (maxLineLength > 0 && maxLineLength != MAX_LINE_LENGTH_NO_WRAP && text.length > maxLineLength) {
      val width = getFontMetrics(font).stringWidth(text.substring(0, maxLineLength))
      processedText = processedText.wrapWith(HtmlChunk.div().attr("width", width))
    }

    @NonNls val css = createCss(maxLineLength != MAX_LINE_LENGTH_NO_WRAP)
    setText(HtmlBuilder()
              .append(HtmlChunk.raw(css))
              .append(processedText.wrapWith("body"))
              .wrapWith("html")
              .toString())
  }

  private fun patchFont() {
    if (type == DslLabelType.COMMENT ) {
      font = ComponentPanelBuilder.getCommentFont(font)
    }
  }

  private fun createCss(wordWrap: Boolean): String {
    val styles = mutableListOf(
      "a, a:link {color:#${ColorUtil.toHex(JBUI.CurrentTheme.Link.Foreground.ENABLED)};}",
      "a:visited {color:#${ColorUtil.toHex(JBUI.CurrentTheme.Link.Foreground.VISITED)};}",
      "a:hover {color:#${ColorUtil.toHex(JBUI.CurrentTheme.Link.Foreground.HOVERED)};}",
      "a:active {color:#${ColorUtil.toHex(JBUI.CurrentTheme.Link.Foreground.PRESSED)};}"
    )

    if (!wordWrap) {
      styles.add("body, p {white-space:nowrap;}")
    }

    return styles.joinToString(" ", "<head><style type='text/css'>", "</style></head>")
  }
}
