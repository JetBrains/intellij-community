// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.newui

import com.intellij.ide.plugins.marketplace.InstallPluginResult
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class InstallPluginTerminalStateTest {
  @Test
  fun `completed operation preserves its result`() {
    val result = InstallPluginResult()

    result.applyTerminalState(
      InstallPluginTerminalState.completed(success = true, showErrors = false, restartRequired = true),
    )

    assertThat(result.success).isTrue()
    assertThat(result.cancel).isFalse()
    assertThat(result.showErrors).isFalse()
    assertThat(result.restartRequired).isTrue()
  }

  @Test
  fun `canceled operation cannot report success or restart`() {
    val result = InstallPluginResult()

    result.applyTerminalState(InstallPluginTerminalState.CANCELED)

    assertThat(result.success).isFalse()
    assertThat(result.cancel).isTrue()
    assertThat(result.showErrors).isFalse()
    assertThat(result.restartRequired).isFalse()
  }

  @Test
  fun `failed operation reports errors without restart`() {
    val result = InstallPluginResult()

    result.applyTerminalState(InstallPluginTerminalState.FAILED)

    assertThat(result.success).isFalse()
    assertThat(result.cancel).isFalse()
    assertThat(result.showErrors).isTrue()
    assertThat(result.restartRequired).isFalse()
  }
}
