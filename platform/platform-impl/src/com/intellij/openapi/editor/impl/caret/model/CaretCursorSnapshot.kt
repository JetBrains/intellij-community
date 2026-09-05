// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.caret.model

import com.intellij.openapi.editor.impl.view.animation.AnimationTimeMark

internal class CaretCursorSnapshot private constructor(
  @JvmField val locations: Array<CaretRectangle>,
  @JvmField val isEnabled: Boolean,
  @JvmField val isShown: Boolean,
  @JvmField val blinkOpacity: Float,
  val startTime: AnimationTimeMark?,
  @JvmField val repaintMetrics: CaretRepaintMetrics,
) {
  fun withEnabled(enabled: Boolean): CaretCursorSnapshot =
    if (enabled == isEnabled) this
    else CaretCursorSnapshot(locations, enabled, isShown, blinkOpacity, startTime, repaintMetrics)

  fun withShown(shown: Boolean, now: AnimationTimeMark): CaretCursorSnapshot = when {
    shown -> CaretCursorSnapshot(locations, isEnabled, true, 1.0f, now, repaintMetrics)
    else -> CaretCursorSnapshot(locations, isEnabled, false, blinkOpacity, startTime, repaintMetrics)
  }

  fun makeFullyOpaque(): CaretCursorSnapshot =
    CaretCursorSnapshot(locations, isEnabled, true, 1.0f, startTime, repaintMetrics)

  fun withStartTime(startTime: AnimationTimeMark): CaretCursorSnapshot =
    CaretCursorSnapshot(locations, isEnabled, isShown, blinkOpacity, startTime, repaintMetrics)

  fun withFrame(
    locations: List<CaretRectangle>?,
    blinkOpacity: Float?,
    now: AnimationTimeMark,
    repaintMetrics: CaretRepaintMetrics,
  ): CaretCursorSnapshot {
    val nextStartTime = if (locations == null) startTime else now
    return CaretCursorSnapshot(
      locations = locations?.toTypedArray() ?: this.locations,
      isEnabled = isEnabled,
      isShown = isShown,
      blinkOpacity = blinkOpacity ?: this.blinkOpacity,
      startTime = nextStartTime,
      repaintMetrics = repaintMetrics,
    )
  }

  companion object {
    val INITIAL: CaretCursorSnapshot = CaretCursorSnapshot(
      locations = arrayOf(CaretRectangle.PLACEHOLDER),
      isEnabled = true,
      isShown = false,
      blinkOpacity = 1.0f,
      startTime = null,
      repaintMetrics = CaretRepaintMetrics.EMPTY,
    )
  }
}
