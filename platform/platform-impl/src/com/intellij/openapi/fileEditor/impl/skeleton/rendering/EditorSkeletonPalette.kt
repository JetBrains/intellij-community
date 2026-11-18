// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl.skeleton.rendering

import com.intellij.ui.ColorUtil
import java.awt.Color

/**
 * The colors of the skeleton blocks on one editor [background].
 *
 * At full contrast, the blocks differ from [background] by 0.13 in Oklab lightness.
 * The blocks are lighter on a dark background and darker on a light background.
 */
internal class EditorSkeletonPalette(val background: Color) {
  private val ramp = createRamp()

  /**
   * Returns the color of the blocks in [state].
   */
  fun blocksColor(state: EditorSkeletonFrameState): Color {
    val color = ramp.colorAt(state.fadeProgress)
    if (state.opacity >= 1.0) {
      return color
    }
    return ColorUtil.withAlpha(color, state.opacity)
  }

  private fun createRamp(): EditorSkeletonOklab.Ramp {
    val delta = if (ColorUtil.isDark(background)) SKELETON_LIGHTNESS_DELTA else -SKELETON_LIGHTNESS_DELTA
    return EditorSkeletonOklab.ramp(background, EditorSkeletonOklab.shiftLightness(background, delta))
  }
}

private const val SKELETON_LIGHTNESS_DELTA = 0.13
