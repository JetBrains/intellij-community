// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui

import com.intellij.ide.IdeTooltipManager
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.ui.components.JBTextArea
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import javax.swing.JButton

@TestApplication
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
  fun installPlainTextIdeTooltipRegistersCustomTooltip() {
    val button = JButton("chip")

    installPlainTextIdeTooltip(button) { "plain\ntext" }

    assertThat(IdeTooltipManager.getInstance().getCustomTooltip(button)).isNotNull()
  }
}
