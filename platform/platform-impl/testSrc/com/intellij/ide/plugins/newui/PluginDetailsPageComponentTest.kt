// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.newui

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class PluginDetailsPageComponentTest {
  @Test
  fun `split legacy details keep installation target options`() {
    assertThat(requiresInstallOptionButton(useSecondaryButtons = false, combinedPluginManagerEnabled = true)).isTrue()
    assertThat(requiresInstallOptionButton(useSecondaryButtons = false, combinedPluginManagerEnabled = false)).isFalse()
    assertThat(requiresInstallOptionButton(useSecondaryButtons = true, combinedPluginManagerEnabled = false)).isTrue()
  }
}
