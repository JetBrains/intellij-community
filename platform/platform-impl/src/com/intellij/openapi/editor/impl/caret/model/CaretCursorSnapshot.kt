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
internal class CaretCursorSnapshot private constructor(
  @JvmField val locations: Array<CaretRectangle>,
  @JvmField val isEnabled: Boolean,
  @JvmField val isShown: Boolean,
  @JvmField val blinkOpacity: Float,
  private val lastActivityAt: AnimationTimeMark?,
  @JvmField val repaintMetrics: CaretRepaintMetrics,
) {
  /**
   * Whether the caret asks to be painted. The editor still has to agree, because a caret in a renderer or in an
   * unfocused editor stays hidden whatever the snapshot says.
   */
  val wantsToBeShown: Boolean get() = isEnabled && isShown

  val isFullyOpaque: Boolean get() = blinkOpacity == FULL_OPACITY

  /// MARK: queries

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
  fun opacityDiffersFrom(other: CaretCursorSnapshot): Boolean {
    return opacityLevelOf(blinkOpacity) != opacityLevelOf(other.blinkOpacity)
  }

  /// MARK: transitions

  fun withEnabled(enabled: Boolean): CaretCursorSnapshot {
    if (enabled == isEnabled) {
      return this
    }
    return CaretCursorSnapshot(locations, enabled, isShown, blinkOpacity, lastActivityAt, repaintMetrics)
  }

  /**
   * Showing the caret counts as activity and makes it fully opaque; hiding it leaves both untouched.
   */
  fun withShown(shown: Boolean, now: AnimationTimeMark): CaretCursorSnapshot {
    if (!shown) {
      return CaretCursorSnapshot(locations, isEnabled, false, blinkOpacity, lastActivityAt, repaintMetrics)
    }
    return CaretCursorSnapshot(locations, isEnabled, true, FULL_OPACITY, now, repaintMetrics)
  }

  /**
   * Shows the caret at full opacity, which is how it looks while the user is busy typing.
   */
  fun shownFullyOpaque(): CaretCursorSnapshot {
    return CaretCursorSnapshot(locations, isEnabled, true, FULL_OPACITY, lastActivityAt, repaintMetrics)
  }

  fun withActivityAt(activityAt: AnimationTimeMark): CaretCursorSnapshot {
    return CaretCursorSnapshot(locations, isEnabled, isShown, blinkOpacity, activityAt, repaintMetrics)
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
  ): CaretCursorSnapshot {
    val nextActivityAt = if (locations == null) lastActivityAt else now
    val nextLocations = locations?.toTypedArray() ?: this.locations
    val nextOpacity = blinkOpacity ?: this.blinkOpacity
    return CaretCursorSnapshot(
      locations = nextLocations,
      isEnabled = isEnabled,
      isShown = isShown,
      blinkOpacity = nextOpacity,
      lastActivityAt = nextActivityAt,
      repaintMetrics = repaintMetrics,
    )
  }

  /**
   * Opacity quantised to what the painter can actually show, so that a change below one level requests no repaint.
   */
  private fun opacityLevelOf(opacity: Float): Int = (opacity * OPACITY_LEVELS).toInt()

  companion object {
    val INITIAL: CaretCursorSnapshot = CaretCursorSnapshot(
      locations = arrayOf(CaretRectangle.PLACEHOLDER),
      isEnabled = true,
      isShown = false,
      blinkOpacity = FULL_OPACITY,
      lastActivityAt = null,
      repaintMetrics = CaretRepaintMetrics.EMPTY,
    )

    private const val FULL_OPACITY = 1.0f

    private const val OPACITY_LEVELS = 255f
  }
}
