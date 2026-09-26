// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import org.jetbrains.annotations.ApiStatus

/**
 * Interface for inter-line shift animation in the gutter.
 * When hovering between two lines, the line numbers (and associated gutter elements)
 * expand away from the hover point to create space for an inter-line indicator.
 */
@ApiStatus.Internal
interface InterLineShiftAnimator {
  /**
   * Returns the vertical offset for the given visual line.
   * - Negative value means shift up
   * - Positive value means shift down
   * - Zero means no shift
   */
  fun getShiftForVisualLine(visualLine: Int): Int

  /**
   * Start or update the shift animation for the given inter-line position.
   *
   * @param lineAbove the visual line above the inter-line gap (-1 if none)
   * @param lineBelow the visual line below the inter-line gap (-1 if none)
   * @param targetShift the target shift amount in pixels
   */
  fun startShift(lineAbove: Int, lineBelow: Int, targetShift: Int)

  /**
   * Stop the shift animation and animate back to 0.
   */
  fun stopShift()
}
