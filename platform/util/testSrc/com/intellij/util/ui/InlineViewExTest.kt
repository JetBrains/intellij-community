// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui

import com.intellij.openapi.application.UI
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.ui.components.JBHtmlPane
import com.intellij.ui.components.JBHtmlPaneConfiguration
import kotlinx.coroutines.Dispatchers
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.awt.Dimension
import java.awt.image.BufferedImage
import javax.swing.event.HyperlinkEvent
import javax.swing.text.html.HTMLDocument

@TestApplication
@Timeout(30)
internal class InlineViewExTest {
  @Test
  fun hoveringInlineCodeLinkKeepsTextPositions(): Unit = timeoutRunBlocking(context = Dispatchers.UI) {
    for (width in 240..600 step 10) checkHoverPositions(width)
  }

  private fun checkHoverPositions(width: Int) {
    val href = "https://example.com"
    val label = "McpServerService.authorizedSession()"
    val pane = object : JBHtmlPane() {
      override fun initializePaneConfiguration(builder: JBHtmlPaneConfiguration.Builder) {
        builder.customStyleSheet("a.sym { color: black; text-decoration: none; } code { background-color: #eeeeee; }")
      }
    }.apply {
      text = """<html><body><p>A provider automatically creates a private authorized session through
        <code><a class="sym" href="$href">$label</a></code> after the link.</p></body></html>""".trimIndent()
      size = Dimension(width, 200)
    }
    pane.ui.getRootView(pane).setSize(pane.width.toFloat(), pane.height.toFloat())
    val document = pane.document as HTMLDocument
    val text = document.getText(0, document.length)
    val start = text.indexOf("McpServerService")
    assertThat(start).isGreaterThanOrEqualTo(0)
    val before = (0 until document.length).map { pane.modelToView2D(it).bounds }
    val beforeImage = if (width == 240) pane.renderedImage() else null

    repeat(2) {
      for (eventType in listOf(HyperlinkEvent.EventType.ENTERED, HyperlinkEvent.EventType.EXITED)) {
        pane.fireHyperlinkUpdate(HyperlinkEvent(pane, eventType, null, href, document.getCharacterElement(start)))
        pane.ui.getRootView(pane).setSize(pane.width.toFloat(), pane.height.toFloat())
        assertThat((0 until document.length).map { pane.modelToView2D(it).bounds })
          .describedAs("text positions after $eventType for $href at width $width")
          .isEqualTo(before)
        if (beforeImage == null) continue
        val image = pane.renderedImage()
        val changedRows = (0 until image.height).filter { y ->
          (0 until image.width).any { x -> beforeImage.getRGB(x, y) != image.getRGB(x, y) }
        }
        if (eventType == HyperlinkEvent.EventType.ENTERED) {
          assertThat(changedRows).describedAs("underline on each wrapped row for $href").hasSizeGreaterThan(1)
        }
        else {
          assertThat(changedRows).describedAs("underline removed for $href").isEmpty()
        }
      }
    }
  }
}

private fun JBHtmlPane.renderedImage(): BufferedImage = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB).also { image ->
  val graphics = image.createGraphics()
  try {
    paint(graphics)
  }
  finally {
    graphics.dispose()
  }
}
