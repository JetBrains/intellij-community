// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide

import com.intellij.openapi.application.UI
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
internal class HelpTooltipMultilineTest {
  private val longText = "Storybook gives agents structured UI context and test feedback. " +
                         "Components, props, stories, and docs define what agents can build. " +
                         "Tests return failures so agents can self-correct."

  @Test
  fun `short title is single-line`() {
    assertThat(isMultiline(HelpTooltip().setPlainTextTitle("Run tests"))).isFalse()
  }

  @Test
  fun `long plain title is multiline`() {
    assertThat(longText.length).isLessThan(250)
    assertThat(isMultiline(HelpTooltip().setPlainTextTitle(longText))).isTrue()
  }

  @Test
  fun `title with its own narrow width is multiline`() {
    val title = HtmlChunk.div().attr("width", 200).addText(longText).wrapWith(HtmlChunk.html())
    assertThat(isMultiline(HelpTooltip().setTitle(title))).isTrue()
  }

  @Test
  fun `title with a line break is multiline`() {
    val title = HtmlChunk.html().addText("Run").child(HtmlChunk.br()).addText("tests")
    assertThat(isMultiline(HelpTooltip().setTitle(title))).isTrue()
  }

  @Test
  fun `description with its own narrow width is multiline`() {
    val description = HtmlChunk.div().attr("width", 200).addText(longText)
    assertThat(isMultiline(HelpTooltip().setDescription(description))).isTrue()
  }

  private fun isMultiline(tooltip: HelpTooltip): Boolean = timeoutRunBlocking {
    withContext(Dispatchers.UI) {
      tooltip.createTipPanel()
      tooltip.isMultilineForTests()
    }
  }
}
