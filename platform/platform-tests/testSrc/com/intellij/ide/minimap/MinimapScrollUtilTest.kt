// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.minimap

import com.intellij.ide.minimap.geometry.MinimapGeometryData
import junit.framework.TestCase

/**
 * Pure-arithmetic tests for [MinimapScrollUtil] - translation of minimap clicks and thumb drags into document scroll offsets.
 */
class MinimapScrollUtilTest : TestCase() {

  private fun geometry(
    minimapHeight: Int = 100,
    areaStart: Int = 0,
    thumbStart: Int = 0,
    thumbHeight: Int = 20,
  ) = MinimapGeometryData(
    minimapHeight = minimapHeight,
    areaStart = areaStart,
    areaEnd = areaStart + minimapHeight,
    thumbStart = thumbStart,
    thumbHeight = thumbHeight,
  )

  private fun assertNonDecreasing(values: List<Int>) {
    for (i in 1 until values.size) {
      assertTrue("expected non-decreasing values, got $values", values[i] >= values[i - 1])
    }
  }

  // --- targetScrollOffsetForPoint ---

  fun testClickIsMonotonicTopToBottom() {
    val g = geometry(minimapHeight = 100)
    val offsets = listOf(0, 10, 25, 50, 75, 90, 100).map {
      MinimapScrollUtil.targetScrollOffsetForPoint(it, g, contentHeight = 1000, viewportHeight = 200)!!
    }
    assertNonDecreasing(offsets)
  }

  fun testClickCentersViewportOnPosition() {
    // Clicking the vertical middle of the minimap centers the viewport on the middle of the document.
    val minimapHeight = 100
    val contentHeight = 1000
    val viewportHeight = 200
    val offset = MinimapScrollUtil.targetScrollOffsetForPoint(
      y = minimapHeight / 2,
      geometry = geometry(minimapHeight = minimapHeight),
      contentHeight = contentHeight,
      viewportHeight = viewportHeight,
    )
    assertEquals(contentHeight / 2 - viewportHeight / 2, offset)
  }

  fun testAreaStartActsAsVerticalShift() {
    // areaStart is a vertical shift of the click window: a click at y with areaStart=s equals a click at y+s with no shift.
    val shift = 20
    val shifted = MinimapScrollUtil.targetScrollOffsetForPoint(
      y = 30, geometry = geometry(minimapHeight = 100, areaStart = shift), contentHeight = 1000, viewportHeight = 200,
    )
    val unshifted = MinimapScrollUtil.targetScrollOffsetForPoint(
      y = 30 + shift, geometry = geometry(minimapHeight = 100, areaStart = 0), contentHeight = 1000, viewportHeight = 200,
    )
    assertEquals(unshifted, shifted)
  }

  fun testClickBeyondMinimapClampsToBottomEdge() {
    val g = geometry(minimapHeight = 100)
    val atBottom = MinimapScrollUtil.targetScrollOffsetForPoint(y = 100, geometry = g, contentHeight = 1000, viewportHeight = 200)
    val farBeyond = MinimapScrollUtil.targetScrollOffsetForPoint(y = 10_000, geometry = g, contentHeight = 1000, viewportHeight = 200)
    assertEquals(atBottom, farBeyond)
  }

  fun testClickReturnsNullForDegenerateGeometry() {
    assertNull(MinimapScrollUtil.targetScrollOffsetForPoint(50, geometry(minimapHeight = 0), contentHeight = 1000, viewportHeight = 200))
    assertNull(MinimapScrollUtil.targetScrollOffsetForPoint(50, geometry(minimapHeight = 100), contentHeight = 0, viewportHeight = 200))
  }

  // --- targetScrollOffsetForThumbDrag ---

  fun testThumbDragAtTopIsZero() {
    val offset = MinimapScrollUtil.targetScrollOffsetForThumbDrag(
      y = 0, dragOffset = 0, panelHeight = 200,
      geometry = geometry(minimapHeight = 200, thumbHeight = 40), contentHeight = 1000, visibleHeight = 200,
    )
    assertEquals(0, offset)
  }

  fun testThumbDragIsMonotonic() {
    val g = geometry(minimapHeight = 200, thumbHeight = 40)
    val offsets = listOf(0, 20, 40, 80, 120, 160).map {
      MinimapScrollUtil.targetScrollOffsetForThumbDrag(
        y = it, dragOffset = 0, panelHeight = 200, geometry = g, contentHeight = 1000, visibleHeight = 200,
      )!!
    }
    assertNonDecreasing(offsets)
  }

  fun testThumbDragClampsAtBottom() {
    val minimapHeight = 200
    val thumbHeight = 40
    val g = geometry(minimapHeight = minimapHeight, thumbHeight = thumbHeight)
    // Dragging to the lowest valid thumb position and dragging far past it land on the same scroll offset.
    val atMax = MinimapScrollUtil.targetScrollOffsetForThumbDrag(
      y = minimapHeight - thumbHeight, dragOffset = 0, panelHeight = 200, geometry = g, contentHeight = 1000, visibleHeight = 200,
    )
    val farBeyond = MinimapScrollUtil.targetScrollOffsetForThumbDrag(
      y = 10_000, dragOffset = 0, panelHeight = 200, geometry = g, contentHeight = 1000, visibleHeight = 200,
    )
    assertEquals(atMax, farBeyond)
  }

  fun testThumbDragReturnsNullForDegenerateInput() {
    assertNull(
      MinimapScrollUtil.targetScrollOffsetForThumbDrag(
        y = 90, dragOffset = 10, panelHeight = 200,
        geometry = geometry(minimapHeight = 200, thumbHeight = 0),
        contentHeight = 1000, visibleHeight = 200,
      )
    )
    assertNull(
      MinimapScrollUtil.targetScrollOffsetForThumbDrag(
        y = 90, dragOffset = 10, panelHeight = 0,
        geometry = geometry(minimapHeight = 200, thumbHeight = 40),
        contentHeight = 1000, visibleHeight = 200,
      )
    )
  }
}
