// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.newui

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.awt.Dimension
import javax.swing.JComponent

internal class BaselinePanelTest {
  @Test
  fun `button without baseline is centered with row controls`() {
    val panel = BaselinePanel(12, false)
    val baseComponent = TestComponent(Dimension(80, 20), 10)
    val textButton = TestComponent(Dimension(40, 24), 12)
    val iconButton = TestComponent(Dimension(24, 24), -1)

    panel.add(baseComponent)
    panel.addButtonComponent(textButton)
    panel.addButtonComponent(iconButton)

    panel.size = panel.preferredSize
    panel.doLayout()

    assertThat(iconButton.y).isEqualTo(textButton.y)
  }

  private class TestComponent(
    private val preferredSize: Dimension,
    private val baseline: Int,
  ) : JComponent() {
    override fun getPreferredSize(): Dimension = Dimension(preferredSize)

    override fun getBaseline(width: Int, height: Int): Int = baseline
  }
}