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
import java.awt.geom.Rectangle2D
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.time.Duration.Companion.milliseconds

/// MARK: what the animation asks the editor for

/**
 * Measures where every caret has to be painted right now.
 */
@RequiresEdt(generateAssertion = false /* IJPL-115548 */)
internal fun EditorImpl.caretPlacements(): List<CaretPlacement> {
  return caretModel.allCarets.map { caret -> caretPlacement(caret) }
}

internal fun EditorImpl.caretAnimationSettings(): CaretAnimationSettings {
  val blinkPeriod = settings.caretBlinkPeriod.milliseconds.coerceAtLeast(MIN_BLINK_PERIOD)
  val blinksSmoothly = !shouldDisableAnimations() && settings.isSmoothCaretBlinking
  val moveDurationMs = Registry.intValue(MOVE_DURATION_REGISTRY_KEY).coerceAtLeast(1)
  return CaretAnimationSettings(
    blinkPeriod = blinkPeriod,
    isBlinking = settings.isBlinkCaret,
    blinksSmoothly = blinksSmoothly,
    easing = caretEasing(),
    moveDuration = moveDurationMs.milliseconds,
  )
}

/**
 * Asks the animation cache to hold the content behind [locations], so the frames that follow can be blitted.
 */
internal fun EditorImpl.prefetchCaretFrames(locations: List<CaretRectangle>) {
  val cacheKey = caretCacheKeyOf(locations)
  view.cacheAreasForRepaint(cacheKey) { cacheRectanglesFor(locations) }
}

/// MARK: geometry details

@RequiresEdt(generateAssertion = false /* IJPL-115548 */)
private fun EditorImpl.caretPlacement(caret: Caret): CaretPlacement {
  val isRtl = caret.isAtRtlLocation
  val visualPosition = caret.visualPosition
  val origin = visualPositionToPoint2D(visualPosition.leanRight(!isRtl))
  val isAtBoundary = !isRtl && inlayModel.hasInlineElementAt(visualPosition)
  return CaretPlacement(
    caret = caret,
    x = origin.x,
    y = origin.y,
    logicalPosition = caret.logicalPosition,
    visualColumnAdjustment = caret.visualColumnAdjustment,
    isAtBoundary = isAtBoundary,
    width = caretWidth(visualPosition, origin.x, isRtl, isAtBoundary),
    isRtl = isRtl,
  )
}

/**
 * How wide the caret is: the distance to the neighbouring column, capped at one space where an inline inlay makes
 * that distance arbitrarily wide.
 */
@RequiresEdt(generateAssertion = false /* IJPL-115548 */)
private fun EditorImpl.caretWidth(
  visualPosition: VisualPosition,
  originX: Double,
  isRtl: Boolean,
  isAtBoundary: Boolean,
): Float {
  val neighbour = visualPositionToPoint2D(visualPosition.nextColumn(isRtl))
  val spanWidth = abs(neighbour.x - originX).toFloat()
  if (!isAtBoundary) {
    return spanWidth
  }
  val oneSpaceWidth = ceil(view.plainSpaceWidth.toDouble()).toFloat()
  return min(spanWidth, oneSpaceWidth)
}

/**
 * The column the caret spans towards, which is the previous one in right-to-left text.
 */
private fun VisualPosition.nextColumn(isRtl: Boolean): VisualPosition {
  val step = if (isRtl) -1 else 1
  val neighbourColumn = max(0, column + step)
  return VisualPosition(line, neighbourColumn, isRtl)
}

/**
 * How far the caret sits from the start of the visual line its logical position maps to.
 */
private val Caret.visualColumnAdjustment: Int
  get() {
    val anchor = editor.logicalToVisualPosition(logicalPosition)
    val onAnchorLine = anchor.line == visualPosition.line
    val afterAnchor = visualPosition.column > anchor.column
    return when {
      onAnchorLine && afterAnchor -> visualPosition.column - anchor.column
      else -> 0
    }
  }

/// MARK: settings details

private fun EditorImpl.caretEasing(): CaretEasing = when (settings.caretEasing) {
  EditorSettings.CaretEasing.SNAPPY, null -> CaretEasing.SNAPPY
  EditorSettings.CaretEasing.GLIDING -> CaretEasing.GLIDING
}

/// MARK: cache prefetch details

@RequiresEdt(generateAssertion = false /* IJPL-115548 */)
private fun EditorImpl.cacheRectanglesFor(locations: List<CaretRectangle>): List<Rectangle2D> {
  val rectangles = view.caretRectanglesForLocations(locations.toTypedArray(), CARET_CACHE_RECTANGLE_MARGIN)
  return rectangles.map { rectangle -> rectangle.coerceAtLeastEmpty() }
}

/**
 * Identifies a set of caret locations, so the cache can drop a request for content it already holds.
 */
private fun caretCacheKeyOf(locations: List<CaretRectangle>): EditorAnimationCacheKey {
  var hash = locations.size
  for (location in locations) {
    hash = hash * CACHE_KEY_HASH_FACTOR + location.contentHash
  }
  return EditorAnimationCacheKey(hash)
}

/// MARK: constants

/**
 * A blink faster than this is a strobe rather than a caret, so the configured period is floored here.
 */
private val MIN_BLINK_PERIOD = 10.milliseconds

private const val MOVE_DURATION_REGISTRY_KEY = "editor.smooth.caret.duration"

private const val CACHE_KEY_HASH_FACTOR = 31
