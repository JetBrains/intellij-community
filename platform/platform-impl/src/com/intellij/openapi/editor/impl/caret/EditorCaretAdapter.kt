// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.caret

import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.EditorSettings
import com.intellij.openapi.editor.VisualPosition
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.impl.caret.model.CARET_CACHE_RECTANGLE_MARGIN
import com.intellij.openapi.editor.impl.caret.model.CaretAnimationSettings
import com.intellij.openapi.editor.impl.caret.model.CaretEasing
import com.intellij.openapi.editor.impl.caret.model.CaretPlacement
import com.intellij.openapi.editor.impl.caret.model.CaretRectangle
import com.intellij.openapi.editor.impl.view.animation.EditorAnimationCacheKey
import com.intellij.openapi.editor.impl.view.animation.coerceAtLeastEmpty
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.time.Duration.Companion.milliseconds

private val MIN_BLINK_PERIOD = 10.milliseconds

@RequiresEdt(generateAssertion = false /* IJPL-115548 */)
internal fun EditorImpl.caretPlacements(): List<CaretPlacement> = caretModel.allCarets.map { caret ->
  val isRtl = caret.isAtRtlLocation
  val visualPosition = caret.visualPosition

  val origin = visualPositionToPoint2D(visualPosition.leanRight(!isRtl))
  val neighbour = visualPositionToPoint2D(
    VisualPosition(visualPosition.line, max(0, visualPosition.column + (if (isRtl) -1 else 1)), isRtl)
  )

  val isAtBoundary = !isRtl && inlayModel.hasInlineElementAt(visualPosition)
  val spanWidth = abs(neighbour.x - origin.x).toFloat()
  val width = when {
    isAtBoundary -> min(spanWidth, ceil(view.plainSpaceWidth.toDouble()).toFloat())
    else -> spanWidth
  }

  CaretPlacement(caret, origin.x, origin.y, caret.logicalPosition, caret.visualColumnAdjustment, isAtBoundary, width, isRtl)
}

internal fun EditorImpl.caretAnimationSettings(): CaretAnimationSettings = CaretAnimationSettings(
  blinkPeriod = settings.caretBlinkPeriod.milliseconds.coerceAtLeast(MIN_BLINK_PERIOD),
  isBlinking = settings.isBlinkCaret,
  blinksSmoothly = !shouldDisableAnimations() && settings.isSmoothCaretBlinking,
  easing = when (settings.caretEasing) {
    EditorSettings.CaretEasing.SNAPPY, null -> CaretEasing.SNAPPY
    EditorSettings.CaretEasing.GLIDING -> CaretEasing.GLIDING
  },
  moveDuration = Registry.intValue("editor.smooth.caret.duration").coerceAtLeast(1).milliseconds,
)

internal fun EditorImpl.prefetchCaretFrames(locations: List<CaretRectangle>) {
  view.cacheAreasForRepaint(caretCacheKeyOf(locations)) {
    view.caretRectanglesForLocations(locations.toTypedArray(), CARET_CACHE_RECTANGLE_MARGIN).map { it.coerceAtLeastEmpty() }
  }
}

private fun caretCacheKeyOf(locations: List<CaretRectangle>): EditorAnimationCacheKey {
  var hash = locations.size
  for (location in locations) {
    hash = hash * 31 + location.x.hashCode()
    hash = hash * 31 + location.y.hashCode()
    hash = hash * 31 + location.width.hashCode()
  }

  return EditorAnimationCacheKey(hash)
}

private val Caret.visualColumnAdjustment: Int get() {
  val anchor = editor.logicalToVisualPosition(logicalPosition)
  return if (anchor.line == visualPosition.line && visualPosition.column > anchor.column) visualPosition.column - anchor.column else 0
}
