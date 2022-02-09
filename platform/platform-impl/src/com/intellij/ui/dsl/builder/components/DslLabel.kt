// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.dsl.builder.components

import com.intellij.lang.documentation.DocumentationMarkup.EXTERNAL_LINK_ICON
import com.intellij.openapi.ui.panel.ComponentPanelBuilder
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.ui.ColorUtil
import com.intellij.ui.dsl.UiDslException
import com.intellij.ui.dsl.builder.HyperlinkEventAction
import com.intellij.ui.dsl.builder.MAX_LINE_LENGTH_NO_WRAP
import com.intellij.util.ui.HTMLEditorKitBuilder
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import java.awt.Dimension
import javax.swing.JEditorPane
import javax.swing.event.HyperlinkEvent
import javax.swing.text.DefaultCaret

/**
 * Denied content and reasons
 */
private val DENIED_TAGS = mapOf(
  Regex("<html>", RegexOption.IGNORE_CASE) to "tag <html> inserted automatically and shouldn't be used",
  Regex("<body>", RegexOption.IGNORE_CASE) to "tag <body> inserted automatically and shouldn't be used",
  Regex("""<a\s+href\s*=\s*(""|'')\s*>""", RegexOption.IGNORE_CASE) to "empty href like <a href=''> is denied, use <a> instead",
)

private const val LINK_GROUP = "link"
private val BROWSER_LINK_REGEX = Regex("""<a\s+href\s*=\s*['"]?(?<href>https?://[^>'"]*)['"]?\s*>(?<link>[^<]*)</a>""",
                                       setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))

@ApiStatus.Internal
enum class DslLabelType {
  LABEL,
  COMMENT
}

@ApiStatus.Internal
class DslLabel(private val type: DslLabelType) : JEditorPane() {

  var action: HyperlinkEventAction? = null

  var maxLineLength: Int = MAX_LINE_LENGTH_NO_WRAP
    set(value) {
      field = value
      updateEditorPaneText()
    }

  @Nls
  private var userText: String? = null

  init {
    contentType = UIUtil.HTML_MIME
    editorKit = HTMLEditorKitBuilder().build()

    // JEditorPane.setText updates cursor and requests scrolling to cursor position if scrollable is used. Disable it
    (caret as DefaultCaret).updatePolicy = DefaultCaret.NEVER_UPDATE

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

  override fun setText(@Nls t: String?) {
    userText = t
    updateEditorPaneText()
  }

  private fun updateEditorPaneText() {
    val text = userText
    if (text == null) {
      super.setText(null)
      return
    }

    for ((regex, reason) in DENIED_TAGS) {
      if (regex.find(text, 0) != null) {
        throw UiDslException("Invalid html: $reason, text: $text")
      }
    }

    @Suppress("HardCodedStringLiteral")
    var processedText = text.replace("<a>", "<a href=''>", ignoreCase = true)
    processedText = appendExternalLinkIcons(processedText)
    var body = HtmlChunk.body()
    if (maxLineLength > 0 && maxLineLength != MAX_LINE_LENGTH_NO_WRAP && text.length > maxLineLength) {
      val width = getFontMetrics(font).stringWidth(text.substring(0, maxLineLength))
      body = body.attr("width", width)
    }

    @NonNls val css = createCss(maxLineLength != MAX_LINE_LENGTH_NO_WRAP)
    super.setText(HtmlBuilder()
                    .append(HtmlChunk.raw(css))
                    .append(HtmlChunk.raw(processedText).wrapWith(body))
                    .wrapWith(HtmlChunk.html())
                    .toString())

    // There is a bug in JDK: if JEditorPane gets height = 0 (text is null) then it never gets correct preferred size afterwards
    // Below is a simple workaround to fix that, see details in BasicTextUI.getPreferredSize
    // See also https://stackoverflow.com/questions/49273118/jeditorpane-getpreferredsize-not-always-working-in-java-9
    size = Dimension(0, 0)
  }

  @Nls
  private fun appendExternalLinkIcons(@Nls text: String): String {
    val matchers = BROWSER_LINK_REGEX.findAll(text)
    if (!matchers.any()) {
      return text
    }

    val result = StringBuilder()
    val externalLink = EXTERNAL_LINK_ICON.toString()
    var i = 0
    for (matcher in matchers) {
      val linkEnd = matcher.groups[LINK_GROUP]!!.range.last
      result.append(text.substring(i..linkEnd))
      result.append(externalLink)
      i = linkEnd + 1
    }
    result.append(text.substring(i))

    @Suppress("HardCodedStringLiteral")
    return result.toString()
  }

  private fun patchFont() {
    if (type == DslLabelType.COMMENT) {
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
