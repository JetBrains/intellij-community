// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui

import com.intellij.agent.workbench.prompt.core.AgentPromptContextItem
import com.intellij.agent.workbench.prompt.core.AgentPromptContextRendererIds
import com.intellij.agent.workbench.prompt.ui.context.AgentPromptScreenshotContextItem
import com.intellij.ide.IdeTooltipManager
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.ui.components.JBTextArea
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.awt.Color
import java.awt.image.BufferedImage
import java.util.concurrent.TimeUnit
import javax.swing.JLabel
import javax.swing.JButton
import javax.swing.JPanel

@TestApplication
@Timeout(value = 2, unit = TimeUnit.MINUTES)
class AgentPromptPlainTextTooltipTest {
  @Test
  fun buildPlainTextTooltipComponentPreservesRawMultilineText() {
    val tooltipText = "<html>tag</html>\nnext line"

    val component = buildPlainTextTooltipComponent(tooltipText)

    assertThat(component.text).isEqualTo(tooltipText)
    assertThat(component.lineWrap).isFalse()
    assertThat(component.wrapStyleWord).isFalse()
  }

  @Test
  fun createPlainTextIdeTooltipBuildsTextAreaOnShow() {
    val button = JButton("chip")
    val tooltip = createPlainTextIdeTooltip(button) { "first\nsecond" }

    val beforeShowMethod = tooltip.javaClass.getDeclaredMethod("beforeShow")
    beforeShowMethod.isAccessible = true
    val shown = beforeShowMethod.invoke(tooltip) as Boolean

    assertThat(shown).isTrue()
    assertThat(tooltip.tipComponent).isInstanceOf(JBTextArea::class.java)
    assertThat((tooltip.tipComponent as JBTextArea).text).isEqualTo("first\nsecond")
  }

  @Test
  fun createContextChipIdeTooltipBuildsImagePreviewForScreenshotItem() {
    val item = buildScreenshotContextItem()
    val button = JButton("chip")
    val tooltip = createContextChipIdeTooltip(button) { ContextEntry(item = item) }

    try {
      val shown = invokeBeforeShow(tooltip)

      assertThat(shown).isTrue()
      assertThat(tooltip.tipComponent).isInstanceOf(JPanel::class.java)
      assertThat((tooltip.tipComponent as JPanel).components.filterIsInstance<JLabel>().any { it.icon != null }).isTrue()
    }
    finally {
      AgentPromptScreenshotContextItem.deleteScreenshotContextFileIfPresent(item)
    }
  }

  @Test
  fun createContextChipIdeTooltipFallsBackToPlainTextForRegularItem() {
    val item = AgentPromptContextItem(
      rendererId = AgentPromptContextRendererIds.SNIPPET,
      title = "Snippet",
      body = "first\nsecond",
    )
    val button = JButton("chip")
    val tooltip = createContextChipIdeTooltip(button) { ContextEntry(item = item) }

    val shown = invokeBeforeShow(tooltip)

    assertThat(shown).isTrue()
    assertThat(tooltip.tipComponent).isInstanceOf(JBTextArea::class.java)
  }

  @Test
  fun installPlainTextIdeTooltipRegistersCustomTooltip() {
    val button = JButton("chip")

    installPlainTextIdeTooltip(button) { "plain\ntext" }

    assertThat(IdeTooltipManager.getInstance().getCustomTooltip(button)).isNotNull()
  }

  private fun invokeBeforeShow(tooltip: Any): Boolean {
    val beforeShowMethod = tooltip.javaClass.getDeclaredMethod("beforeShow")
    beforeShowMethod.isAccessible = true
    return beforeShowMethod.invoke(tooltip) as Boolean
  }

  private fun buildScreenshotContextItem(): AgentPromptContextItem {
    val image = BufferedImage(20, 10, BufferedImage.TYPE_INT_ARGB)
    val graphics = image.createGraphics()
    try {
      graphics.color = Color.RED
      graphics.fillRect(0, 0, image.width, image.height)
    }
    finally {
      graphics.dispose()
    }
    return AgentPromptScreenshotContextItem.buildScreenshotContextItem(
      title = "Pasted image",
      screenshot = image,
      sourceId = "test.image",
      source = "testImage",
      tempFilePrefix = "agent-prompt-preview-test-",
    )
  }
}
