// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui

import com.intellij.testFramework.junit5.TestApplication
import com.intellij.ui.scale.ScaleContext
import com.intellij.ui.scale.ScaleType
import com.intellij.util.system.OS
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import javax.swing.SwingUtilities

@TestApplication
internal class AppWindowIconPreloadTest {
  private val scale1 = ScaleContext.of(arrayOf(ScaleType.SYS_SCALE.of(1.0), ScaleType.USR_SCALE.of(1.0)))
  private val scale2 = ScaleContext.of(arrayOf(ScaleType.SYS_SCALE.of(2.0), ScaleType.USR_SCALE.of(1.0)))

  @AfterEach
  fun clearPreload() {
    setPreloadedAppWindowIcons(null)
  }

  @Test
  fun `the rendering off the EDT gives the images of the OS at their sizes`() {
    assertThat(SwingUtilities.isEventDispatchThread()).isFalse()

    val icons = renderAppWindowIcons(scale1)

    val sizes = when (OS.CURRENT) {
      OS.Windows -> listOf(32, 16)
      OS.macOS -> listOf(128)
      else -> listOf(128, 32)
    }
    assertThat(icons.images.map { it.getWidth(null) }).isEqualTo(sizes)
    assertThat(icons.dockImage != null).isEqualTo(OS.CURRENT == OS.macOS)
  }

  @Test
  fun `the preloaded images are taken once and only at the scale they were rendered at`() {
    val icons = renderAppWindowIcons(scale2)

    setPreloadedAppWindowIcons(icons)
    assertThat(takePreloadedAppWindowIcons(scale1)).isNull()
    // a window of another scale renders its own images, so the preload is gone
    assertThat(takePreloadedAppWindowIcons(scale2)).isNull()

    setPreloadedAppWindowIcons(icons)
    assertThat(takePreloadedAppWindowIcons(scale2)).isSameAs(icons)
    assertThat(takePreloadedAppWindowIcons(scale2)).isNull()
  }
}
