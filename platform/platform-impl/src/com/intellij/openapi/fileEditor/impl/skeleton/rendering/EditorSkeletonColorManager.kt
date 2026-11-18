// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl.skeleton.rendering

import com.intellij.openapi.fileEditor.impl.skeleton.EditorSkeleton
import java.awt.Color
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

internal class EditorSkeletonColorManager(
  skeletonDelayMs: Long,
  private val nowMs: () -> Long,
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

  fun frameColors(): FrameColors {
    val background = background.get()
    val palette = palette.updateAndGet { if (it.background == background) it else EditorSkeletonPalette(background) }
    val now = nowMs()
    if (fadeInStartTime.compareAndSet(Long.MIN_VALUE, now)) {
      return FrameColors(background, background)
    }
    val state = timeline.stateAt(now - fadeInStartTime.get())
    return FrameColors(background, palette.blocksColor(state))
  }

  @Suppress("UseJBColor")
  private fun snapshotBackground(): Color = Color(EditorSkeleton.EDITOR_BACKGROUND_COLOR.rgb, true)

  data class FrameColors(val background: Color, val blocks: Color)
}
