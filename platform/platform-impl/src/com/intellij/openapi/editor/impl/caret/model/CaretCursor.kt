// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.caret.model

import com.intellij.openapi.editor.impl.view.animation.AnimationTimeMark
import kotlin.time.Duration

/**
 * Everything the painter needs to draw the carets, captured as one immutable value so that the locations and the
 * opacity it reads always belong to the same frame.
 *
 * @param lastActivityAt when the caret was last shown or moved, or `null` while it has never been shown.
 *                       The quiet period that holds the blink off is measured from this moment.
 */
internal class CaretCursor private constructor(
  private val locations: List<CaretRectangle>,
  private val isEnabled: Boolean,
  private val isShown: Boolean,
  private val blinkOpacity: Float,
  private val repaintMetrics: CaretRepaintMetrics,
  private val lastActivityAt: AnimationTimeMark?,
) {
  /**
   * Whether the caret asks to be painted. The editor still has to agree, because a caret in a renderer or in an
   * unfocused editor stays hidden whatever the snapshot says.
   */
  fun wantsToBeShown(): Boolean {
    return isEnabled && isShown
  }

  fun isFullyOpaque(): Boolean {
    return blinkOpacity == FULL_OPACITY
  }

  fun locations(): List<CaretRectangle> {
    return locations
  }

  fun isEnabled(): Boolean {
    return isEnabled
  }

  fun isShown(): Boolean {
    return isShown
  }

  fun blinkOpacity(): Float {
    return blinkOpacity
  }

  fun repaintMetrics(): CaretRepaintMetrics {
    return repaintMetrics
  }

  /**
   * How long the caret has been idle at [now], or [Duration.INFINITE] while it has never been shown.
   */
  fun quietTimeAt(now: AnimationTimeMark): Duration {
    val lastActivityAt = lastActivityAt ?: return Duration.INFINITE
    return now - lastActivityAt
  }

  /**
   * Whether [other] would be painted at a visibly different opacity than this snapshot.
   */
  fun opacityDiffersFrom(other: CaretCursor): Boolean {
    return opacityLevelOf(blinkOpacity) != opacityLevelOf(other.blinkOpacity)
  }

  fun withEnabled(enabled: Boolean): CaretCursor {
    if (enabled == isEnabled) {
      return this
    }
    return CaretCursor(locations, enabled, isShown, blinkOpacity, repaintMetrics, lastActivityAt)
  }

  /**
   * Showing the caret counts as activity and makes it fully opaque; hiding it leaves both untouched.
   */
  fun withShown(shown: Boolean, now: AnimationTimeMark): CaretCursor {
    if (!shown) {
      return CaretCursor(locations, isEnabled, false, blinkOpacity, repaintMetrics, lastActivityAt)
    }
    return CaretCursor(locations, isEnabled, true, FULL_OPACITY, repaintMetrics, now)
  }

  /**
   * Shows the caret at full opacity, which is how it looks while the user is busy typing.
   */
  fun shownFullyOpaque(): CaretCursor {
    return CaretCursor(locations, isEnabled, true, FULL_OPACITY, repaintMetrics, lastActivityAt)
  }

  fun withActivityAt(activityAt: AnimationTimeMark): CaretCursor {
    return CaretCursor(locations, isEnabled, isShown, blinkOpacity, repaintMetrics, activityAt)
  }

  /**
   * Adopts the metrics the caret is repainted with, so that a repaint of this snapshot covers the caret it paints.
   */
  fun withRepaintMetrics(repaintMetrics: CaretRepaintMetrics): CaretCursor {
    if (repaintMetrics == this.repaintMetrics) {
      return this
    }
    return CaretCursor(locations, isEnabled, isShown, blinkOpacity, repaintMetrics, lastActivityAt)
  }

  /**
   * Applies one animation step. A `null` argument means that part of the step did not change.
   *
   * A caret that moved counts as activity, so the quiet period starts over and the blink stays awake.
   */
  fun withStep(
    locations: List<CaretRectangle>?,
    blinkOpacity: Float?,
    now: AnimationTimeMark,
    repaintMetrics: CaretRepaintMetrics,
  ): CaretCursor {
    val nextActivityAt = if (locations == null) lastActivityAt else now
    val nextLocations = locations ?: this.locations
    val nextOpacity = blinkOpacity ?: this.blinkOpacity
    return CaretCursor(
      locations = nextLocations,
      isEnabled = isEnabled,
      isShown = isShown,
      blinkOpacity = nextOpacity,
      repaintMetrics = repaintMetrics,
      lastActivityAt = nextActivityAt,
    )
  }

  /**
   * Opacity quantised to what the painter can actually show, so that a change below one level requests no repaint.
   */
  private fun opacityLevelOf(opacity: Float): Int {
    return (opacity * OPACITY_LEVELS).toInt()
  }

  companion object {
    val INITIAL: CaretCursor = CaretCursor(
      locations = listOf(CaretRectangle.PLACEHOLDER),
      isEnabled = true,
      isShown = false,
      blinkOpacity = FULL_OPACITY,
      repaintMetrics = CaretRepaintMetrics.EMPTY,
      lastActivityAt = null,
    )

    private const val FULL_OPACITY = 1.0f
    private const val OPACITY_LEVELS = 255f
  }
}
