// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.dsl.builder.components

import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.ui.ColorUtil
import com.intellij.ui.components.JBLabel
import com.intellij.ui.dsl.UiDslException
import com.intellij.ui.dsl.builder.HyperlinkEventAction
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import javax.swing.event.HyperlinkEvent
import javax.swing.event.HyperlinkListener

private val DENIED_TAGS = listOf("<html>", "<body>")

@ApiStatus.Internal
internal class DslLabel(@NlsContexts.Label text: String) : JBLabel(text) {

  var action: HyperlinkEventAction? = null

  override fun createHyperlinkListener(): HyperlinkListener {
    return HyperlinkListener { e ->
      when (e?.eventType) {
        HyperlinkEvent.EventType.ACTIVATED -> action?.hyperlinkActivated(e)
        HyperlinkEvent.EventType.ENTERED -> action?.hyperlinkEntered(e)
        HyperlinkEvent.EventType.EXITED -> action?.hyperlinkExited(e)
      }
    }
  }

  fun setHtmlText(@Nls text: String, maxLineLength: Int) {
    for (deniedTag in DENIED_TAGS) {
      if (text.contains(deniedTag)) {
        throw UiDslException("Text contains denied tag $deniedTag: $text")
      }
    }

    setCopyable(true)
    isAllowAutoWrapping = true
    verticalTextPosition = TOP
    isFocusable = false

    @NonNls val css = """
      <head><style type="text/css">
      a, a:link {color:#${ColorUtil.toHex(JBUI.CurrentTheme.Link.Foreground.ENABLED)};}
      a:visited {color:#${ColorUtil.toHex(JBUI.CurrentTheme.Link.Foreground.VISITED)};}
      a:hover {color:#${ColorUtil.toHex(JBUI.CurrentTheme.Link.Foreground.HOVERED)};}
      a:active {color:#${ColorUtil.toHex(JBUI.CurrentTheme.Link.Foreground.PRESSED)};}
      </style></head>
      """.trimIndent()

    @Suppress("HardCodedStringLiteral")
    var processedText = HtmlChunk.raw(text.replace("<a>", "<a href=''>", ignoreCase = true))
    processedText = if (maxLineLength > 0 && text.length > maxLineLength) {
      val width = getFontMetrics(font).stringWidth(text.substring(0, maxLineLength))
      processedText.wrapWith(HtmlChunk.div().attr("width", width))
    }
    else {
      processedText.wrapWith(HtmlChunk.div())
    }
    setText(HtmlBuilder()
              .append(HtmlChunk.raw(css))
              .append(processedText.wrapWith("body"))
              .wrapWith("html")
              .toString())
  }
}
