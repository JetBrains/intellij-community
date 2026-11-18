// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl.skeleton.rendering

import com.intellij.openapi.editor.colors.EditorColorsManager
import java.awt.Color
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

internal class EditorSkeletonColorManager(
  skeletonDelayMs: Long,
  animationEnabled: Boolean,
  animationDurationMs: Long,
) {
  private val timeline = EditorSkeletonTimeline(
    fadeInMs = skeletonDelayMs,
    pulseEnabled = animationEnabled,
    pulseDurationMs = animationDurationMs,
  )
  private val background = AtomicReference(snapshotBackground())
  private val palette = AtomicReference(EditorSkeletonPalette(background.get()))
  private val fadeInStartTime = AtomicLong(Long.MIN_VALUE)

  fun reloadColorScheme() {
    background.set(snapshotBackground())
  }

  fun colorsAt(timeMs: Long): SkeletonFrameColor {
    val background = background.get()
    val palette = palette.updateAndGet {
      if (it.background == background) it else EditorSkeletonPalette(background)
    }
    if (fadeInStartTime.compareAndSet(Long.MIN_VALUE, timeMs)) {
      return SkeletonFrameColor(background, background)
    }
    val state = timeline.stateAt(timeMs - fadeInStartTime.get())
    return SkeletonFrameColor(background, palette.blocksColor(state))
  }

  @Suppress("UseJBColor")
  private fun snapshotBackground(): Color {
    return Color(EditorColorsManager.getInstance().globalScheme.defaultBackground.rgb, true)
  }
}
