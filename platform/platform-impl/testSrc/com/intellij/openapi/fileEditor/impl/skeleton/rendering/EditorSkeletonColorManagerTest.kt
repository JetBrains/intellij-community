// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("UseJBColor")

package com.intellij.openapi.fileEditor.impl.skeleton.rendering

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.awt.Color

internal class EditorSkeletonColorManagerTest {
  @Test
  fun `the first frame starts the fade at the background`() {
    val manager = manager(DARK_BACKGROUND)
    assertThat(manager.colorsAt(5_000)).isEqualTo(SkeletonFrameColor(DARK_BACKGROUND, DARK_BACKGROUND))
  }

  @Test
  fun `the fade counts from the first frame`() {
    val manager = manager(DARK_BACKGROUND)
    manager.colorsAt(10_000)
    val halfway = manager.colorsAt(10_000 + FADE_IN_MS / 2).blocks
    assertThat(halfway).isNotEqualTo(DARK_BACKGROUND).isNotEqualTo(DARK_FULL_CONTRAST)
    assertThat(manager.colorsAt(10_000 + FADE_IN_MS).blocks).isEqualTo(DARK_FULL_CONTRAST)
  }

  @Test
  fun `a new background changes the next frame`() {
    val manager = manager(DARK_BACKGROUND)
    manager.colorsAt(0)
    manager.setBackground(Color.WHITE)
    assertThat(manager.colorsAt(FADE_IN_MS)).isEqualTo(SkeletonFrameColor(Color.WHITE, Color(0xD4D4D4)))
  }

  @Test
  fun `the background of a frame is a copy of the given color`() {
    val manager = manager(DynamicColor(0x1E1F22))
    val background = manager.colorsAt(0).background
    assertThat(background.javaClass).isEqualTo(Color::class.java)
    assertThat(background).isEqualTo(DARK_BACKGROUND)
  }

  private fun manager(background: Color): EditorSkeletonColorManager {
    return EditorSkeletonColorManager(
      background = background,
      fadeInMs = FADE_IN_MS,
      pulseEnabled = false,
      pulseDurationMs = 1500,
    )
  }

  private class DynamicColor(rgb: Int) : Color(rgb)
}

private const val FADE_IN_MS = 1000L
private val DARK_BACKGROUND = Color(0x1E1F22)
private val DARK_FULL_CONTRAST = Color(0x3E3F43)
