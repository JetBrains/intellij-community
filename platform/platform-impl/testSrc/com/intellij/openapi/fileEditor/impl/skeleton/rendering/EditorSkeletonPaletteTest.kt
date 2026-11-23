// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("UseJBColor")

package com.intellij.openapi.fileEditor.impl.skeleton.rendering

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.awt.Color

internal class EditorSkeletonPaletteTest {
  @Test
  fun `the fade starts at the background color`() {
    val palette = EditorSkeletonPalette(DARK_BACKGROUND)
    assertThat(palette.blocksColor(EditorSkeletonFrameState(fadeProgress = 0.0, opacity = 1.0))).isEqualTo(DARK_BACKGROUND)
  }

  @Test
  fun `full contrast is lighter on a dark background`() {
    val palette = EditorSkeletonPalette(DARK_BACKGROUND)
    assertThat(palette.blocksColor(FULL_CONTRAST)).isEqualTo(Color(0x3E3F43))
  }

  @Test
  fun `full contrast is darker on a light background`() {
    val palette = EditorSkeletonPalette(Color.WHITE)
    assertThat(palette.blocksColor(FULL_CONTRAST)).isEqualTo(Color(0xD4D4D4))
  }

  @Test
  fun `a pulse below full opacity makes the blocks translucent`() {
    val palette = EditorSkeletonPalette(DARK_BACKGROUND)
    val color = palette.blocksColor(EditorSkeletonFrameState(fadeProgress = 1.0, opacity = 0.3))
    assertThat(color.rgb and 0xFFFFFF).isEqualTo(0x3E3F43)
    assertThat(color.alpha).isEqualTo(76)
  }
}

private val DARK_BACKGROUND = Color(0x1E1F22)
private val FULL_CONTRAST = EditorSkeletonFrameState(fadeProgress = 1.0, opacity = 1.0)
