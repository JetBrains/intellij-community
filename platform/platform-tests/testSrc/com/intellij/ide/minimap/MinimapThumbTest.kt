// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.minimap

import com.intellij.ide.minimap.thumb.MinimapThumb
import junit.framework.TestCase
import kotlin.math.abs

/**
 * Pure-arithmetic tests for [MinimapThumb] - the viewport-thumb sizing and positioning math.
 */
class MinimapThumbTest : TestCase() {

  private fun assertNonDecreasing(values: List<Int>) {
    for (i in 1 until values.size) {
      assertTrue("expected non-decreasing values, got $values", values[i] >= values[i - 1])
    }
  }

  // --- computeHeight ---

  fun testThumbFillsMinimapWhenNothingToScroll() {
    // content fits within the viewport => no scroll range => thumb spans the whole minimap.
    val minimapHeight = 100
    assertEquals(minimapHeight, MinimapThumb.computeHeight(visibleHeight = minimapHeight, contentHeight = minimapHeight, minimapHeight = minimapHeight))
    assertEquals(minimapHeight, MinimapThumb.computeHeight(visibleHeight = 2 * minimapHeight, contentHeight = minimapHeight, minimapHeight = minimapHeight))
  }

  fun testThumbHeightStaysWithinBounds() {
    val minimapHeight = 100
    for (visible in listOf(1, 10, 50, 99, 100, 200)) {
      val height = MinimapThumb.computeHeight(visibleHeight = visible, contentHeight = 1000, minimapHeight = minimapHeight)
      assertTrue("thumb height $height not within 1..$minimapHeight for visible=$visible", height in 1..minimapHeight)
    }
  }

  fun testThumbHeightGrowsWithVisiblePortion() {
    val heights = listOf(50, 100, 200, 400, 800).map {
      MinimapThumb.computeHeight(visibleHeight = it, contentHeight = 1000, minimapHeight = 100)
    }
    assertNonDecreasing(heights)
  }

  fun testThumbHeightIsZeroWhenNoMinimap() {
    assertEquals(0, MinimapThumb.computeHeight(visibleHeight = 50, contentHeight = 200, minimapHeight = 0))
  }

  // --- computeStart / mapThumbStartToScrollOffset ---

  fun testStartIsZeroAtTop() {
    assertEquals(0, MinimapThumb.computeStart(scrollOffset = 0, scrollRange = 100, minimapHeight = 200, thumbHeight = 40))
  }

  fun testStartReachesMaxAtFullRange() {
    val minimapHeight = 200
    val thumbHeight = 40
    val scrollRange = 100
    // At the end of the scroll range the thumb sits at its lowest position: minimapHeight - thumbHeight.
    assertEquals(
      minimapHeight - thumbHeight,
      MinimapThumb.computeStart(scrollOffset = scrollRange, scrollRange = scrollRange, minimapHeight = minimapHeight, thumbHeight = thumbHeight),
    )
  }

  fun testStartIsMonotonicInScrollOffset() {
    val starts = listOf(0, 25, 50, 75, 100).map {
      MinimapThumb.computeStart(scrollOffset = it, scrollRange = 100, minimapHeight = 200, thumbHeight = 40)
    }
    assertNonDecreasing(starts)
  }

  fun testStartClampsBeyondFullRange() {
    val minimapHeight = 200
    val thumbHeight = 40
    assertEquals(
      minimapHeight - thumbHeight,
      MinimapThumb.computeStart(scrollOffset = 10_000, scrollRange = 100, minimapHeight = minimapHeight, thumbHeight = thumbHeight),
    )
  }

  fun testStartIsZeroWhenNoRoom() {
    assertEquals(0, MinimapThumb.computeStart(scrollOffset = 50, scrollRange = 0, minimapHeight = 200, thumbHeight = 40))
    assertEquals(0, MinimapThumb.computeStart(scrollOffset = 50, scrollRange = 100, minimapHeight = 20, thumbHeight = 20))
    assertEquals(0, MinimapThumb.computeStart(scrollOffset = 50, scrollRange = 100, minimapHeight = 200, thumbHeight = 0))
  }

  fun testThumbStartAndScrollOffsetRoundTrip() {
    val scrollRange = 100
    val minimapHeight = 200
    val thumbHeight = 40
    for (offset in listOf(0, 25, 50, 75, 100)) {
      val start = MinimapThumb.computeStart(scrollOffset = offset, scrollRange = scrollRange, minimapHeight = minimapHeight, thumbHeight = thumbHeight)
      val roundTripped = MinimapThumb.mapThumbStartToScrollOffset(start, scrollRange = scrollRange, minimapHeight = minimapHeight, thumbHeight = thumbHeight)
      // Allow ±1 for integer rounding in the two-way conversion.
      assertTrue("round-trip of scroll offset $offset produced $roundTripped", abs(roundTripped - offset) <= 1)
    }
  }

  fun testMapThumbStartClampsAndGuards() {
    val scrollRange = 100
    // A thumb dragged past the bottom maps to the full scroll range; the top maps to 0.
    assertEquals(scrollRange, MinimapThumb.mapThumbStartToScrollOffset(10_000, scrollRange = scrollRange, minimapHeight = 200, thumbHeight = 40))
    assertEquals(0, MinimapThumb.mapThumbStartToScrollOffset(0, scrollRange = scrollRange, minimapHeight = 200, thumbHeight = 40))
    assertEquals(0, MinimapThumb.mapThumbStartToScrollOffset(80, scrollRange = 0, minimapHeight = 200, thumbHeight = 40))
  }

  // --- computeStartFromDrag ---

  fun testDragAtTopIsZero() {
    assertEquals(0, MinimapThumb.computeStartFromDrag(y = 0, dragOffset = 0, panelHeight = 100, minimapHeight = 300, thumbHeight = 20))
  }

  fun testDragIsMonotonic() {
    val starts = listOf(0, 20, 40, 60, 80).map {
      MinimapThumb.computeStartFromDrag(y = it, dragOffset = 0, panelHeight = 100, minimapHeight = 300, thumbHeight = 20)
    }
    assertNonDecreasing(starts)
  }

  fun testDragClampsAtMax() {
    // When the minimap fits the panel, the thumb top is bounded by minimapHeight - thumbHeight.
    val minimapHeight = 100
    val thumbHeight = 20
    val atMax = MinimapThumb.computeStartFromDrag(y = minimapHeight - thumbHeight, dragOffset = 0, panelHeight = 200, minimapHeight = minimapHeight, thumbHeight = thumbHeight)
    val beyond = MinimapThumb.computeStartFromDrag(y = 10_000, dragOffset = 0, panelHeight = 200, minimapHeight = minimapHeight, thumbHeight = thumbHeight)
    assertEquals(minimapHeight - thumbHeight, atMax)
    assertEquals(atMax, beyond)
  }

  fun testDragDegenerateCasesReturnZero() {
    // thumb >= minimap, or panel <= thumb => no movement.
    assertEquals(0, MinimapThumb.computeStartFromDrag(y = 50, dragOffset = 0, panelHeight = 100, minimapHeight = 30, thumbHeight = 30))
    assertEquals(0, MinimapThumb.computeStartFromDrag(y = 50, dragOffset = 0, panelHeight = 20, minimapHeight = 300, thumbHeight = 30))
  }
}
