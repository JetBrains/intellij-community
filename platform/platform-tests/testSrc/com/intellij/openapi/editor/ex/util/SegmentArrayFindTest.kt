// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.ex.util

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

/**
 * Checks that [SegmentArray.findSegmentIndex] with a hint gives the index of the search without a hint.
 */
internal class SegmentArrayFindTest {
  @Test
  fun `each hint gives the index of the search without a hint`() {
    val segments = segments(0, 1, 3, 4, 8, 9, 10, 15, 16, 20, 21, 22, 23, 30)
    for (offset in 0..segments.lastValidOffset) {
      val expected = segments.findSegmentIndex(offset)
      for (hint in -1..segments.segmentCount) {
        assertThat(segments.findSegmentIndex(offset, hint)).describedAs("offset %d, hint %d", offset, hint).isEqualTo(expected)
      }
    }
  }

  @Test
  fun `each hint reports a gap`() {
    val segments = SegmentArrayWithData(ShortBasedStorage())
    segments.setElementAt(0, 0, 2, 0)
    segments.setElementAt(1, 4, 6, 0)
    for (hint in -1..2) {
      assertThatThrownBy { segments.findSegmentIndex(3, hint) }.isInstanceOf(IllegalStateException::class.java)
    }
  }

  @Test
  fun `each hint gives the index of an array without segments`() {
    val segments = SegmentArrayWithData(ShortBasedStorage())
    assertThat(segments.findSegmentIndex(0, 0)).isEqualTo(0)
    assertThatThrownBy { segments.findSegmentIndex(1, 0) }.isInstanceOf(IllegalStateException::class.java)
  }

  private fun segments(vararg offsets: Int): SegmentArrayWithData {
    val segments = SegmentArrayWithData(ShortBasedStorage())
    for (i in 0 until offsets.size - 1) {
      segments.setElementAt(i, offsets[i], offsets[i + 1], 0)
    }
    return segments
  }
}
