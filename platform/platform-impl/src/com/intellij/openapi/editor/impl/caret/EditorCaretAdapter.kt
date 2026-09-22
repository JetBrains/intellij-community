// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.caret

import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.VisualPosition
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.impl.caret.model.CaretPlacement
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * Measures where every caret has to be painted right now.
 */
@RequiresEdt(generateAssertion = false /* IJPL-115548 */)
internal fun EditorImpl.caretPlacements(): List<CaretPlacement> {
  return caretModel.allCarets.map { caret -> caretPlacement(caret) }
}

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
    visualColumnAdjustment = caret.visualColumnAdjustment(),
    isAtBoundary = isAtBoundary,
    width = caretWidth(visualPosition, origin.x, isRtl, isAtBoundary),
    isRtl = isRtl,
  )
}

/**
 * How wide the caret is: the distance to the neighbouring column, capped at one space where an inline inlay makes
 * that distance arbitrarily wide.
 */
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
private fun Caret.visualColumnAdjustment(): Int {
  val anchor = editor.logicalToVisualPosition(logicalPosition)
  val onAnchorLine = anchor.line == visualPosition.line
  val afterAnchor = visualPosition.column > anchor.column
  return if (onAnchorLine && afterAnchor) {
    visualPosition.column - anchor.column
  } else {
    0
  }
}
