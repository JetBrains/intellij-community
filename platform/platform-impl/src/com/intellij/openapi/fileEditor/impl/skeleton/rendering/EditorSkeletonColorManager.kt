// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl.skeleton.rendering

import java.awt.Color
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Gives the colors of the skeleton frames on [background]. Any thread can call it.
 *
 * The first call of [colorsAt] starts the fade-in, and its blocks have the color of the background.
 * The timeline counts from that call: the blocks fade in over [fadeInMs], then they pulse with the period [pulseDurationMs].
 * The manager keeps a copy of each background color, so a later change of a dynamic color does not reach the frames.
 */
internal class EditorSkeletonColorManager(
  background: Color,
  fadeInMs: Long,
  pulseEnabled: Boolean,
  pulseDurationMs: Long,
) {
  private val timeline = EditorSkeletonTimeline(
    fadeInMs = fadeInMs,
    pulseEnabled = pulseEnabled,
    pulseDurationMs = pulseDurationMs,
  )
  private val background = AtomicReference(copyOf(background))
  private val palette = AtomicReference(EditorSkeletonPalette(this.background.get()))
  private val fadeInStartMs = AtomicLong(Long.MIN_VALUE)

  /**
   * Sets the background of the next frames.
   */
  fun setBackground(background: Color) {
    this.background.set(copyOf(background))
  }

  /**
   * Returns the colors of the frame at [timeMs].
   */
  fun colorsAt(timeMs: Long): SkeletonFrameColor {
    val background = background.get()
    val palette = palette.updateAndGet {
      if (it.background == background) it else EditorSkeletonPalette(background)
    }
    if (fadeInStartMs.compareAndSet(Long.MIN_VALUE, timeMs)) {
      return SkeletonFrameColor(background, background)
    }
    val state = timeline.stateAt(timeMs - fadeInStartMs.get())
    return SkeletonFrameColor(background, palette.blocksColor(state))
  }

  @Suppress("UseJBColor")
  private fun copyOf(color: Color): Color {
    return Color(color.rgb, true)
  }
}
