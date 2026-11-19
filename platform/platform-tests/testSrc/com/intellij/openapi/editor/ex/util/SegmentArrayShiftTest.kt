// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.ex.util

import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Checks [SegmentArray.shiftSegments].
 */
@TestApplication
internal class SegmentArrayShiftTest {
  @Test
  fun `a shift moves each segment from the start index`() {
    val segments = segments(0, 3, 5, 9)
    segments.shiftSegments(1, 2)
    assertThat(bounds(segments)).containsExactly(0 to 3, 5 to 7, 7 to 11)
  }

  @Test
  fun `a shift before offset 0 logs one error and moves the segments`() {
    val segments = segments(0, 3, 5, 9)
    val error = LoggedErrorProcessor.executeAndReturnLoggedError {
      segments.shiftSegments(1, -6)
    }
    assertThat(error).hasMessage("Error shifting segments: myStarts[1] = -3, myEnds[1] = -1")
    assertThat(bounds(segments)).containsExactly(0 to 3, -3 to -1, -1 to 3)
  }

  @Test
  fun `a shift from the segment count moves no segment`() {
    val segments = segments(0, 3, 5)
    segments.shiftSegments(2, -10)
    assertThat(bounds(segments)).containsExactly(0 to 3, 3 to 5)
  }

  private fun segments(vararg offsets: Int): SegmentArrayWithData {
    val segments = SegmentArrayWithData(ShortBasedStorage())
    for (i in 0 until offsets.size - 1) {
      segments.setElementAt(i, offsets[i], offsets[i + 1], 0)
    }
    return segments
  }

  private fun bounds(segments: SegmentArray): List<Pair<Int, Int>> {
    return (0 until segments.segmentCount).map { segments.getSegmentStart(it) to segments.getSegmentEnd(it) }
  }
}
