// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("EditorCaretAdapter")

package com.intellij.openapi.editor.impl

import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.EditorSettings
import com.intellij.openapi.editor.VisualPosition
import com.intellij.openapi.editor.impl.caret.model.CaretAnimationSettings
import com.intellij.openapi.editor.impl.caret.model.CaretEasing
import com.intellij.openapi.editor.impl.caret.model.CaretPlacement
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

private const val MIN_BLINK_PERIOD_MS = 10L

@RequiresEdt
internal fun caretPlacements(editor: EditorImpl): List<CaretPlacement> = editor.caretModel.allCarets.map { caret ->
  val isRtl = caret.isAtRtlLocation
  val visualPosition = caret.visualPosition

  val origin = editor.visualPositionToPoint2D(visualPosition.leanRight(!isRtl))
  val neighbour = editor.visualPositionToPoint2D(
    VisualPosition(visualPosition.line, max(0, visualPosition.column + (if (isRtl) -1 else 1)), isRtl)
  )

  val isAtBoundary = !isRtl && editor.inlayModel.hasInlineElementAt(visualPosition)
  val spanWidth = abs(neighbour.x - origin.x).toFloat()
  val width = when {
    isAtBoundary -> min(spanWidth, ceil(editor.view.plainSpaceWidth.toDouble()).toFloat())
    else -> spanWidth
  }

  val visualColumnAdjustment = caret.visualColumnAdjustment
  CaretPlacement(caret, origin.x, origin.y, caret.logicalPosition, visualColumnAdjustment, isAtBoundary, width, isRtl)
}

internal fun caretAnimationSettings(settings: EditorSettings, animationsDisabled: Boolean): CaretAnimationSettings = CaretAnimationSettings(
  blinkPeriodMs = settings.caretBlinkPeriod.toLong().coerceAtLeast(MIN_BLINK_PERIOD_MS),
  isBlinking = settings.isBlinkCaret,
  blinksSmoothly = !animationsDisabled && settings.isSmoothCaretBlinking,
  easing = when (settings.caretEasing) {
    EditorSettings.CaretEasing.SNAPPY -> CaretEasing.SNAPPY
    EditorSettings.CaretEasing.GLIDING -> CaretEasing.GLIDING
  },
  moveDurationMs = Registry.intValue("editor.smooth.caret.duration").coerceAtLeast(1).toDouble(),
)

private val Caret.visualColumnAdjustment: Int get() {
  val anchor = editor.logicalToVisualPosition(logicalPosition)
  return if (anchor.line == visualPosition.line && visualPosition.column > anchor.column) visualPosition.column - anchor.column else 0
}
