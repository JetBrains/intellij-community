// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl.skeleton.rendering

import com.intellij.openapi.fileEditor.impl.skeleton.EditorSkeleton
import com.intellij.ui.ColorUtil
import com.intellij.util.animation.Easing
import java.awt.Color
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.sin

internal class EditorSkeletonColorManager(
  private val skeletonDelayMs: Long,
  private val nowMs: () -> Long,
  animationEnabled: Boolean,
  private val animationDurationMs: Long,
) {
  private val animationEnabled = animationEnabled && animationDurationMs > 0
  private val background = AtomicReference(snapshotBackground())
  private val ramp = AtomicReference(createRamp(background.get()))
  private val fadeInStartTime = AtomicLong(Long.MIN_VALUE)
  private val curve = Easing.bezier(0.4, 0.0, 1.0, 1.0)

  fun reloadColorScheme() {
    background.set(snapshotBackground())
  }

  fun frameColors(): FrameColors {
    val background = background.get()
    val ramp = ramp.updateAndGet { if (it.colorAt(0.0) == background) it else createRamp(background) }
    val now = nowMs()
    if (fadeInStartTime.compareAndSet(Long.MIN_VALUE, now)) {
      return FrameColors(background, background)
    }
    val elapsedMs = now - fadeInStartTime.get()
    val progress = if (skeletonDelayMs <= 0) 1.0
    else curve.calc(elapsedMs.coerceIn(0, skeletonDelayMs).toDouble() / skeletonDelayMs.toDouble())
    if (progress < 1.0) return FrameColors(background, ramp.colorAt(progress))

    val color = ramp.colorAt(1.0)
    if (!animationEnabled) return FrameColors(background, color)

    val animationElapsedMs = elapsedMs - skeletonDelayMs.coerceAtLeast(0)
    val t = (animationElapsedMs % animationDurationMs).toDouble() / animationDurationMs.toDouble()
    val opacity = 0.3 + 0.35 * (sin(2 * Math.PI * (t - 0.75)) + 1)
    return FrameColors(background, ColorUtil.withAlpha(color, opacity))
  }

  private fun createRamp(background: Color): EditorSkeletonOklab.Ramp {
    val delta = if (ColorUtil.isDark(background)) SKELETON_LIGHTNESS_DELTA else -SKELETON_LIGHTNESS_DELTA
    return EditorSkeletonOklab.ramp(background, EditorSkeletonOklab.shiftLightness(background, delta))
  }

  @Suppress("UseJBColor")
  private fun snapshotBackground(): Color = Color(EditorSkeleton.EDITOR_BACKGROUND_COLOR.rgb, true)

  data class FrameColors(val background: Color, val blocks: Color)

  companion object {
    private const val SKELETON_LIGHTNESS_DELTA = 0.13
  }
}
