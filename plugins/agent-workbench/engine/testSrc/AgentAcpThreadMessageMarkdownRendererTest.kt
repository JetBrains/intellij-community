// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.engine.platform

import com.intellij.agent.workbench.engine.ui.AgentAcpThreadMessageMarkdownRenderer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class AgentAcpThreadMessageMarkdownRendererTest {
  @Test
  fun `renders basic markdown fragments`() {
    val html = AgentAcpThreadMessageMarkdownRenderer.renderHtmlBody(
      """
      # Heading

      A **bold** and *italic* value with `inline` code and ~~deleted~~ text.

      - first
      - second

      ```kotlin
      println("<unsafe>")
      ```
      """.trimIndent(),
    )

    assertThat(html).contains("<h1>Heading</h1>")
    assertThat(html).contains("<b>bold</b>")
    assertThat(html).contains("<i>italic</i>")
    assertThat(html).contains("<code>inline</code>")
    assertThat(html).contains("<strike>deleted</strike>")
    assertThat(html).contains("<ul>")
    assertThat(html).contains("<li>first</li>")
    assertThat(html).contains("<pre><code class=\"language-kotlin\">")
    assertThat(html).contains("&lt;unsafe&gt;")
  }

  @Test
  fun `escapes raw html`() {
    val html = AgentAcpThreadMessageMarkdownRenderer.renderHtmlBody(
      """
      before <script>alert('x')</script>

      <div>block</div>
      """.trimIndent(),
    )

    assertThat(html).contains("&lt;script&gt;alert('x')&lt;/script&gt;")
    assertThat(html).contains("&lt;div&gt;block&lt;/div&gt;")
    assertThat(html).doesNotContain("<script>")
    assertThat(html).doesNotContain("<div>block</div>")
  }

  @Test
  fun `renders markdown links`() {
    val html = AgentAcpThreadMessageMarkdownRenderer.renderHtmlBody("Open [JetBrains](https://www.jetbrains.com/).")

    assertThat(html).contains("<a href=\"https://www.jetbrains.com/\">JetBrains</a>")
  }

  @Test
  fun `blank input renders blank html`() {
    assertThat(AgentAcpThreadMessageMarkdownRenderer.renderHtmlBody("")).isEmpty()
    assertThat(AgentAcpThreadMessageMarkdownRenderer.renderHtmlBody(" \n\t ")).isEmpty()
  }

  @Test
  fun `rendering new text does not retain old html`() {
    val first = AgentAcpThreadMessageMarkdownRenderer.renderHtmlBody("**old**")
    val second = AgentAcpThreadMessageMarkdownRenderer.renderHtmlBody("`new`")

    assertThat(first).contains("old")
    assertThat(second).contains("new")
    assertThat(second).doesNotContain("old")
  }
}
