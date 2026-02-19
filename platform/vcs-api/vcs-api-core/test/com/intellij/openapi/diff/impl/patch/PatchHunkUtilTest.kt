// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.diff.impl.patch

import com.intellij.diff.util.Range
import com.intellij.openapi.diff.impl.patch.PatchHunkUtil.getChangeOnlyRanges
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

class PatchHunkUtilTest {
  @ParameterizedTest(name = "{0}")
  @MethodSource("simpleRanges")
  fun `getChangeOnlyRanges for a simple one-range hunk calculates that one range`(range: Range) {
    val hunk = PatchHunk(range.start1, range.end1, range.start2, range.end2).apply {
      repeat(range.end1 - range.start1) { addLine(REMOVE_LINE) }
      repeat(range.end2 - range.start2) { addLine(ADD_LINE) }
    }

    assertThat(getChangeOnlyRanges(hunk))
      .hasSize(1)
      .first().isEqualTo(range)
  }

  companion object {
    @JvmStatic
    fun simpleRanges(): Array<Range> = arrayOf(
      Range(0, 1, 0, 1),
      Range(0, 0, 0, 10),
      Range(0, 5, 0, 10),
      Range(3, 5, 3, 3),
      Range(5, 5, 3, 4),
    )

    val REMOVE_LINE = PatchLine(PatchLine.Type.REMOVE, "").apply { isSuppressNewLine = true }
    val ADD_LINE = PatchLine(PatchLine.Type.ADD, "").apply { isSuppressNewLine = true }
  }
}