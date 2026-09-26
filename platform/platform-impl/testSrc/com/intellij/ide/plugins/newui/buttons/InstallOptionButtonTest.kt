// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.newui.buttons

import com.intellij.ide.plugins.newui.ColorButton
import com.intellij.ide.ui.LafManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.Action

@TestApplication
internal class InstallOptionButtonTest {
  companion object {
    @JvmStatic
    @BeforeAll
    fun beforeAll() {
      LafManager.getInstance()
    }
  }

  @Test
  fun `default button keeps legacy explicit widths`() {
    val button = InstallOptionButton()

    button.action = testAction()
    assertThat(button.isPreferredSizeSet).isTrue()
    assertThat(button.preferredSize.width).isEqualTo(explicitWidth(80))

    button.setTextAndSize(null)
    assertThat(button.isPreferredSizeSet).isTrue()
    assertThat(button.preferredSize.width).isEqualTo(explicitWidth(72))
  }

  @Test
  fun `natural button stays naturally sized with remote development options`() {
    val option = object : AnAction() {
      override fun actionPerformed(event: AnActionEvent) = Unit
    }
    val button = InstallOptionButton(useNaturalWidth = true)

    button.setOptions(listOf(option))
    assertThat(button.isPreferredSizeSet).isFalse()
    assertThat(button.options).hasSize(1)
    button.action = testAction()
    assertThat(button.isPreferredSizeSet).isFalse()
    button.setTextAndSize(null)

    assertThat(button.isPreferredSizeSet).isFalse()
    assertThat(button.options).hasSize(1)
  }

  @Test
  fun `status text keeps explicit width in both modes`() {
    val legacyButton = InstallOptionButton()
    val naturalButton = InstallOptionButton(useNaturalWidth = true)

    legacyButton.setTextAndSize("Installing")
    naturalButton.setTextAndSize("Installing")

    assertThat(legacyButton.isPreferredSizeSet).isTrue()
    assertThat(naturalButton.isPreferredSizeSet).isTrue()
    assertThat(legacyButton.preferredSize.width).isEqualTo(explicitWidth(80))
    assertThat(naturalButton.preferredSize.width).isEqualTo(explicitWidth(80))

    legacyButton.setEnabled(false, "Failed")
    naturalButton.setEnabled(false, "Failed")

    assertThat(legacyButton.preferredSize.width).isEqualTo(explicitWidth(80))
    assertThat(naturalButton.preferredSize.width).isEqualTo(explicitWidth(80))
  }

  private fun testAction(): Action = object : AbstractAction() {
    override fun actionPerformed(event: ActionEvent?) = Unit
  }

  private fun explicitWidth(width: Int): Int {
    val reference = InstallOptionButton(useNaturalWidth = true)
    ColorButton.setWidth(reference, width)
    return reference.preferredSize.width
  }
}
