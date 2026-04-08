// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui.context

import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
class PromptWindowVisibilityControllerTest {
  @Test
  fun hideAndRestoreReopensPromptWindowThatWasVisible() {
    val promptWindow = TestPromptWindow(isVisible = true)
    val controller = PromptWindowVisibilityController(promptWindow)

    controller.hide()
    controller.restore()

    assertThat(promptWindow.isVisible).isTrue()
    assertThat(promptWindow.toFrontCalls).isEqualTo(1)
    assertThat(promptWindow.requestFocusCalls).isEqualTo(1)
  }

  @Test
  fun restoreDoesNothingWhenPromptWindowStartedHidden() {
    val promptWindow = TestPromptWindow(isVisible = false)
    val controller = PromptWindowVisibilityController(promptWindow)

    controller.hide()
    controller.restore()

    assertThat(promptWindow.isVisible).isFalse()
    assertThat(promptWindow.toFrontCalls).isZero()
    assertThat(promptWindow.requestFocusCalls).isZero()
  }

  @Test
  fun repeatedRestoreDoesNotRefocusPromptWindowAgain() {
    val promptWindow = TestPromptWindow(isVisible = true)
    val controller = PromptWindowVisibilityController(promptWindow)

    controller.hide()
    controller.restore()
    controller.restore()

    assertThat(promptWindow.toFrontCalls).isEqualTo(1)
    assertThat(promptWindow.requestFocusCalls).isEqualTo(1)
  }

  private class TestPromptWindow(
    override var isVisible: Boolean,
  ) : PromptWindowHandle {
    var toFrontCalls: Int = 0
      private set

    var requestFocusCalls: Int = 0
      private set

    override fun toFront() {
      toFrontCalls++
    }

    override fun requestFocus() {
      requestFocusCalls++
    }
  }
}
