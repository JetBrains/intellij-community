// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.minimap

import com.intellij.ide.minimap.model.MinimapLineProjection
import com.intellij.ide.minimap.model.MinimapSourceSoftWrap
import junit.framework.TestCase

/**
 * Pure-logic tests for [MinimapLineProjection] - the logical<->projected line mapping used by the minimap to
 * hide folded lines and expand soft-wrapped ones; this guards the arithmetic in the feature against accidental
 * breakage from unrelated changes.
 */
class MinimapLineProjectionTest : TestCase() {

  fun testIdentityMapsEveryLineOneToOne() {
    val projection = MinimapLineProjection.identity(4)

    assertEquals(4, projection.logicalLineCount)
    assertEquals(4, projection.projectedLineCount)
    for (line in 0 until 4) {
      assertEquals(line, projection.logicalToProjectedLine(line))
      assertEquals(line, projection.projectedToLogicalLine(line))
      assertEquals(1, projection.logicalLineProjectedSpan(line))
      assertFalse(projection.isLineInCollapsedRegion(line))
    }
  }

  fun testCollapsedRegionHidesInteriorLines() {
    // Lines 1..3 collapsed: the header line 1 stays visible, lines 2 and 3 are hidden.
    val projection = MinimapLineProjection.create(
      logicalLineCount = 5,
      collapsedRegionsByLine = listOf(1 to 3),
      lineSpanOverrides = emptyMap(),
    )

    assertEquals(3, projection.projectedLineCount)

    // Visible lines keep their own projected slot; hidden lines fold onto the header's projected line.
    assertEquals(0, projection.logicalToProjectedLine(0))
    assertEquals(1, projection.logicalToProjectedLine(1))
    assertEquals(1, projection.logicalToProjectedLine(2))
    assertEquals(1, projection.logicalToProjectedLine(3))
    assertEquals(2, projection.logicalToProjectedLine(4))

    // Reverse mapping skips the hidden lines.
    assertEquals(0, projection.projectedToLogicalLine(0))
    assertEquals(1, projection.projectedToLogicalLine(1))
    assertEquals(4, projection.projectedToLogicalLine(2))

    // Hidden lines have no visible projected slot.
    assertNull(projection.logicalToVisibleProjectedLine(2))
    assertNull(projection.logicalToVisibleProjectedLine(3))
    assertEquals(0, projection.logicalToVisibleProjectedLine(0))
    assertEquals(2, projection.logicalToVisibleProjectedLine(4))

    // Collapsed-region membership covers the header and the hidden interior, but not the lines around it.
    assertFalse(projection.isLineInCollapsedRegion(0))
    assertTrue(projection.isLineInCollapsedRegion(1))
    assertTrue(projection.isLineInCollapsedRegion(2))
    assertTrue(projection.isLineInCollapsedRegion(3))
    assertFalse(projection.isLineInCollapsedRegion(4))

    assertEquals(1, projection.collapsedRegions.size)
    val region = projection.collapsedRegions.single()
    assertEquals(1, region.startLine)
    assertEquals(3, region.endLine)
    assertEquals(2, region.hiddenLineCount)
  }

  fun testSoftWrapExpandsLineToMultipleProjectedLines() {
    // Line 1 spans 3 projected lines (e.g. wrapped across 3 visual rows).
    val projection = MinimapLineProjection.create(
      logicalLineCount = 3,
      collapsedRegionsByLine = emptyList(),
      lineSpanOverrides = mapOf(1 to 3),
    )

    assertEquals(5, projection.projectedLineCount)
    assertEquals(3, projection.logicalLineProjectedSpan(1))

    assertEquals(0, projection.logicalToProjectedLine(0))
    assertEquals(1, projection.logicalToProjectedLine(1)) // first slot of the wrapped line
    assertEquals(4, projection.logicalToProjectedLine(2))

    // All three projected rows resolve back to logical line 1.
    assertEquals(1, projection.projectedToLogicalLine(1))
    assertEquals(1, projection.projectedToLogicalLine(2))
    assertEquals(1, projection.projectedToLogicalLine(3))

    // Slot index within the wrapped line.
    assertEquals(0, projection.projectedLineSlotIndex(1))
    assertEquals(1, projection.projectedLineSlotIndex(2))
    assertEquals(2, projection.projectedLineSlotIndex(3))
    assertEquals(0, projection.projectedLineSlotIndex(4))
  }

  fun testOverlappingCollapsedRegionsAreIgnored() {
    // The second region overlaps the first and must be dropped, so only lines 2..4 are hidden.
    val projection = MinimapLineProjection.create(
      logicalLineCount = 10,
      collapsedRegionsByLine = listOf(1 to 4, 2 to 3),
      lineSpanOverrides = emptyMap(),
    )

    assertEquals(1, projection.collapsedRegions.size)
    assertEquals(1, projection.collapsedRegions.single().startLine)
    assertEquals(4, projection.collapsedRegions.single().endLine)
    // 10 lines, 3 hidden (2..4) => 7 projected lines.
    assertEquals(7, projection.projectedLineCount)
  }

  fun testSourceSoftWrapsLookup() {
    val wraps = listOf(MinimapSourceSoftWrap(startOffset = 10, indentColumns = 2))
    val projection = MinimapLineProjection.create(
      logicalLineCount = 3,
      collapsedRegionsByLine = emptyList(),
      lineSpanOverrides = emptyMap(),
      sourceSoftWrapsByLine = mapOf(1 to wraps),
    )

    assertEquals(wraps, projection.sourceSoftWraps(1))
    assertNull(projection.sourceSoftWraps(0)) // no entry for this line
  }

  fun testOutOfRangeQueriesAreSafe() {
    val projection = MinimapLineProjection.identity(3)

    assertNull(projection.logicalToProjectedLine(-1))
    assertNull(projection.logicalToProjectedLine(3))
    assertNull(projection.projectedToLogicalLine(-1))
    assertNull(projection.projectedToLogicalLine(3))
    assertNull(projection.projectedLineSlotIndex(3))
    assertNull(projection.sourceSoftWraps(-1))
    assertNull(projection.sourceSoftWraps(5))
    assertFalse(projection.isLineInCollapsedRegion(-1))
    assertFalse(projection.isLineInCollapsedRegion(3))
    assertEquals(0, projection.logicalLineProjectedSpan(-1))
    assertEquals(0, projection.logicalLineProjectedSpan(3))
  }

  fun testEmptyAndNegativeLineCounts() {
    for (projection in listOf(MinimapLineProjection.identity(0), MinimapLineProjection.create(-5, emptyList(), emptyMap()))) {
      assertEquals(0, projection.logicalLineCount)
      assertEquals(0, projection.projectedLineCount)
      assertNull(projection.logicalToProjectedLine(0))
      assertNull(projection.projectedToLogicalLine(0))
      assertTrue(projection.collapsedRegions.isEmpty())
    }
  }
}
