// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.util

import com.intellij.diff.util.LineRange
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.github.pullrequest.ui.diff.util.LineRangeUtil
import org.junit.jupiter.api.Test

class LineRangeUtilTest {

  @Test
  fun `doesn't allow comments on last empty line overwritten with non-empty line`() {
    val leftRange = listOf(LineRange(15, 18))
    val rightRange = listOf(LineRange(15, 21))
    val leftChangedRanges = listOf(LineRange(18, 19))
    val rightChangedRanges = listOf(LineRange(19, 21))

    val result = LineRangeUtil.extract(leftRange, rightRange, leftChangedRanges, rightChangedRanges)

    assertThat(result == listOf(LineRange(15, 18), LineRange(19, 21)))
  }

  @Test
  fun `changes at the end of the file with empty lines`() {
    val leftRange = listOf(LineRange(15, 18))
    val rightRange = listOf(LineRange(15, 19))
    val leftChangedRanges = listOf(LineRange(19, 18))
    val rightChangedRanges = listOf(LineRange(18, 19))

    val result = LineRangeUtil.extract(leftRange, rightRange, leftChangedRanges, rightChangedRanges)

    assertThat(result == listOf(LineRange(15, 19)))
  }

  @Test
  fun `1 line deletion and 2 lines insertion`() {
    val leftRange = listOf(LineRange(2, 16))
    val rightRange = listOf(LineRange(2, 16))
    val leftChangedRanges = listOf(LineRange(7, 5), LineRange(12, 13))
    val rightChangedRanges = listOf(LineRange(5, 7), LineRange(13, 12))

    val result = LineRangeUtil.extract(leftRange, rightRange, leftChangedRanges, rightChangedRanges)

    assertThat(result == listOf(LineRange(2, 16)))
  }

  @Test
  fun `3 lines modification`() {
    val leftRange = listOf(LineRange(12, 18))
    val rightRange = listOf(LineRange(12, 21))
    val leftChangedRanges = listOf(LineRange(15, 18))
    val rightChangedRanges = listOf(LineRange(18, 21))

    val result = LineRangeUtil.extract(leftRange, rightRange, leftChangedRanges, rightChangedRanges)

    assertThat(result == listOf(LineRange(12, 21)))
  }

  @Test
  fun `3 lines modification, empty line inserted at the end`() {
    val leftRange = listOf(LineRange(12, 18))
    val rightRange = listOf(LineRange(12, 21))
    val leftChangedRanges = listOf(LineRange(15, 18))
    val rightChangedRanges = listOf(LineRange(18, 22))

    val result = LineRangeUtil.extract(leftRange, rightRange, leftChangedRanges, rightChangedRanges)

    assertThat(result == listOf(LineRange(12, 21)))
  }


  @Test
  fun `multiple changes with space between them`() {
    val leftRange = listOf(LineRange(0, 6), LineRange(8, 21))
    val rightRange = listOf(LineRange(0, 6), LineRange(8, 25))
    val leftChangedRanges = listOf(LineRange(2, 3), LineRange(11, 16), LineRange(25, 21))
    val rightChangedRanges = listOf(LineRange(3, 22), LineRange(16, 19), LineRange(21, 25))

    val result = LineRangeUtil.extract(leftRange, rightRange, leftChangedRanges, rightChangedRanges)

    assertThat(result == listOf(LineRange(0, 6), LineRange(8, 25)))
  }
}