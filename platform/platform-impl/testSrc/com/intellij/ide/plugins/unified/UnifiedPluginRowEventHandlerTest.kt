// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.unified

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.awt.event.InputEvent
import java.awt.event.MouseEvent
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel

internal class UnifiedPluginRowEventHandlerTest {
  @Test
  fun `buttons and their children are action controls`() {
    val button = JButton()
    val buttonChild = JLabel()
    button.add(buttonChild)

    assertThat(isPluginRowActionControl(button)).isTrue()
    assertThat(isPluginRowActionControl(buttonChild)).isTrue()
  }

  @Test
  fun `ordinary row content remains row activation content`() {
    val rowContent = JPanel()
    val label = JLabel()
    rowContent.add(label)

    assertThat(isPluginRowActionControl(rowContent)).isFalse()
    assertThat(isPluginRowActionControl(label)).isFalse()
  }

  @Test
  fun `control click requests a context menu only on macOS`() {
    val component = JPanel()
    val rightClick = MouseEvent(
      component,
      MouseEvent.MOUSE_CLICKED,
      0,
      0,
      0,
      0,
      1,
      false,
      MouseEvent.BUTTON3,
    )
    val controlClick = MouseEvent(
      component,
      MouseEvent.MOUSE_CLICKED,
      0,
      InputEvent.CTRL_DOWN_MASK,
      0,
      0,
      1,
      false,
      MouseEvent.BUTTON1,
    )

    assertThat(isPluginRowContextMenuEvent(rightClick, isMac = true)).isTrue()
    assertThat(isPluginRowContextMenuEvent(rightClick, isMac = false)).isTrue()
    assertThat(isPluginRowContextMenuEvent(controlClick, isMac = true)).isTrue()
    assertThat(isPluginRowContextMenuEvent(controlClick, isMac = false)).isFalse()
  }

  @Test
  fun `toggle selection modifier follows platform conventions`() {
    val component = JPanel()
    val controlClick = MouseEvent(
      component,
      MouseEvent.MOUSE_CLICKED,
      0,
      InputEvent.CTRL_DOWN_MASK,
      0,
      0,
      1,
      false,
      MouseEvent.BUTTON1,
    )
    val metaClick = MouseEvent(
      component,
      MouseEvent.MOUSE_CLICKED,
      0,
      InputEvent.META_DOWN_MASK,
      0,
      0,
      1,
      false,
      MouseEvent.BUTTON1,
    )

    assertThat(isPluginRowToggleSelectionEvent(controlClick, isMac = true)).isFalse()
    assertThat(isPluginRowToggleSelectionEvent(controlClick, isMac = false)).isTrue()
    assertThat(isPluginRowToggleSelectionEvent(metaClick, isMac = true)).isTrue()
    assertThat(isPluginRowToggleSelectionEvent(metaClick, isMac = false)).isFalse()
  }
}
