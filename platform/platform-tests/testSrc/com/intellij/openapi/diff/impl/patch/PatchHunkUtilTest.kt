// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.diff.impl.patch

import com.intellij.diff.util.LineRange
import com.intellij.diff.util.Side
import com.intellij.openapi.diff.impl.patch.PatchHunkUtil.getLinesInRange
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class PatchHunkUtilTest {

  @Test
  fun `test getAfterLinesInRange for full patch`() {
    val hunk = hunk(0,
                    listOf("0",
                           "1",
                           "2"),
                    listOf("3",
                           "4",
                           "5"),
                    listOf("31",
                           "41",
                           "51"),
                    listOf("6",
                           "7",
                           "8"))
    assertEquals(listOf(), hunk.getAfterTextLinesInRange(4, 4))
    assertEquals(listOf("41"), hunk.getAfterTextLinesInRange(4, 5))
    assertEquals(listOf("41", "51", "6"), hunk.getAfterTextLinesInRange(4, 7))
    assertEquals(listOf("2", "31"), hunk.getAfterTextLinesInRange(2, 4))
    assertEquals(listOf("2", "31", "41", "51", "6"), hunk.getAfterTextLinesInRange(2, 7))
  }

  @Test
  fun `test getAfterLinesInRange for partial patch`() {
    val hunk = hunk(0,
                    listOf("0",
                           "1",
                           "2"),
                    listOf(),
                    listOf("31",
                           "41",
                           "51"),
                    listOf())
    assertEquals(listOf(), hunk.getAfterTextLinesInRange(4, 4))
    assertEquals(listOf("41"), hunk.getAfterTextLinesInRange(4, 5))
    assertEquals(listOf("41", "51"), hunk.getAfterTextLinesInRange(4, 7))
    assertEquals(listOf("2", "31"), hunk.getAfterTextLinesInRange(2, 4))
    assertEquals(listOf("2", "31", "41", "51"), hunk.getAfterTextLinesInRange(2, 7))
  }

  @Test
  fun `test getAfterLinesInRange for multi-change patch`() {
    val hunk = PatchHunk(0, 4, 0, 4).apply {
      ctx("1")
      rem("2")
      add("3")
      ctx("4")
      ctx("5")
      rem("6")
      add("7")
    }
    assertEquals(listOf(), hunk.getAfterTextLinesInRange(5, 5))
    assertEquals(listOf("1", "3", "4", "5", "7"), hunk.getAfterTextLinesInRange(0, 5))
    assertEquals(listOf("4", "5", "7"), hunk.getAfterTextLinesInRange(2, 5))
    assertEquals(listOf("7"), hunk.getAfterTextLinesInRange(4, 5))
  }

  private fun PatchHunk.getAfterTextLinesInRange(start: Int, end: Int) =
    getLinesInRange(this, Side.RIGHT, LineRange(start, end)).map { it.text }

  private fun hunk(start: Int,
                   ctxBefore: List<String>,
                   removed: List<String>,
                   added: List<String>,
                   ctxAfter: List<String>): PatchHunk {
    val endBefore = start + ctxBefore.size + removed.size + ctxAfter.size
    val endAfter = start + ctxBefore.size + added.size + ctxAfter.size
    return PatchHunk(start, endBefore, start, endAfter).apply {
      ctxBefore.forEach { addLine(PatchLine(PatchLine.Type.CONTEXT, it)) }
      removed.forEach { addLine(PatchLine(PatchLine.Type.REMOVE, it)) }
      added.forEach { addLine(PatchLine(PatchLine.Type.ADD, it)) }
      ctxAfter.forEach { addLine(PatchLine(PatchLine.Type.CONTEXT, it)) }
    }
  }

  private fun PatchHunk.ctx(text: String) {
    addLine(PatchLine(PatchLine.Type.CONTEXT, text))
  }

  private fun PatchHunk.rem(text: String) {
    addLine(PatchLine(PatchLine.Type.REMOVE, text))
  }

  private fun PatchHunk.add(text: String) {
    addLine(PatchLine(PatchLine.Type.ADD, text))
  }
}