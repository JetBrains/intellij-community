// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.engine.platform

import com.intellij.agent.workbench.engine.ui.AgentAcpThreadHyperlinkHandler
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Path
import javax.swing.JEditorPane
import javax.swing.event.HyperlinkEvent
import javax.swing.text.AttributeSet
import javax.swing.text.Element
import javax.swing.text.html.HTML

class AgentAcpThreadHyperlinkHandlerTest {
  private val projectBasePath = "/repo"

  @Test
  fun `parses project relative file link with line and column`() {
    val location = AgentAcpThreadHyperlinkHandler.parseLocalFileLocation("src/Main.kt:12:3", projectBasePath)

    assertThat(location?.path).isEqualTo(Path.of("/repo/src/Main.kt"))
    assertThat(location?.line).isEqualTo(11)
    assertThat(location?.column).isEqualTo(2)
  }

  @Test
  fun `parses project relative filename with line`() {
    val location = AgentAcpThreadHyperlinkHandler.parseLocalFileLocation("README.md:12", projectBasePath)

    assertThat(location?.path).isEqualTo(Path.of("/repo/README.md"))
    assertThat(location?.line).isEqualTo(11)
    assertThat(location?.column).isNull()
  }

  @Test
  fun `parses absolute file link with fragment line`() {
    val location = AgentAcpThreadHyperlinkHandler.parseLocalFileLocation("file:///repo/src/Main.kt#L12", projectBasePath)

    assertThat(location?.path).isEqualTo(Path.of("/repo/src/Main.kt"))
    assertThat(location?.line).isEqualTo(11)
    assertThat(location?.column).isNull()
  }

  @Test
  fun `ignores web links`() {
    assertThat(AgentAcpThreadHyperlinkHandler.parseLocalFileLocation("https://www.jetbrains.com", projectBasePath)).isNull()
  }

  @Test
  fun `extracts href from source element before event description`() {
    val pane = JEditorPane("text/html", """
      <html><body><a href="src/Main.kt:12">file</a></body></html>
    """.trimIndent())
    val linkElement = pane.document.defaultRootElement.findElementWithHref()!!
    val event = HyperlinkEvent(this, HyperlinkEvent.EventType.ACTIVATED, null, "fallback", linkElement)

    assertThat(AgentAcpThreadHyperlinkHandler.extractTarget(event)).isEqualTo("src/Main.kt:12")
  }

  private fun Element.findElementWithHref(): Element? {
    val anchorAttributes = attributes.getAttribute(HTML.Tag.A) as? AttributeSet
    if (attributes.getAttribute(HTML.Attribute.HREF) != null || anchorAttributes?.getAttribute(HTML.Attribute.HREF) != null) {
      return this
    }
    for (index in 0 until elementCount) {
      getElement(index).findElementWithHref()?.let { return it }
    }
    return null
  }
}
