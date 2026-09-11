// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.newui

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.awt.Insets

internal class PluginImagesComponentTest {
  @Test
  fun `image width accounts for both parent insets`() {
    val viewportWidth = 600

    assertThat(calculatePluginImagesFullWidth(viewportWidth, Insets(16, 16, 0, 0))).isEqualTo(584)
    assertThat(calculatePluginImagesFullWidth(viewportWidth, Insets(16, 16, 0, 16))).isEqualTo(568)
  }

  @Test
  fun `image width does not become negative`() {
    assertThat(calculatePluginImagesFullWidth(20, Insets(0, 16, 0, 16))).isZero()
  }
}
