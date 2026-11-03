// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.impl.event.DocumentEventImpl
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
internal class RepaintAreaTest {
  @Test
  fun `an area keeps a negative start and a large end`() {
    val area = RepaintArea(-1, Int.MAX_VALUE)
    assertThat(area.start).isEqualTo(-1)
    assertThat(area.end).isEqualTo(Int.MAX_VALUE)
  }

  @Test
  fun `a union with NONE gives the other area`() {
    val area = RepaintArea(2, 5)
    assertThat(area.union(RepaintArea.NONE)).isEqualTo(area)
    assertThat(RepaintArea.NONE.union(area)).isEqualTo(area)
    assertThat(RepaintArea.NONE.union(RepaintArea.NONE).isNone()).isTrue()
    assertThat(area.union(RepaintArea(7, 9))).isEqualTo(RepaintArea(2, 9))
  }

  @Test
  fun `an area covers text only when it has at least one character`() {
    assertThat(RepaintArea(2, 3).coversText()).isTrue()
    assertThat(RepaintArea(2, 2).coversText()).isFalse()
    assertThat(RepaintArea.NONE.coversText()).isFalse()
  }

  @Test
  fun `an insertion before the area moves it`() {
    assertThat(RepaintArea(2, 5).movedBy(change(0, 0, 3))).isEqualTo(RepaintArea(5, 8))
  }

  @Test
  fun `an insertion at the start grows the area over the new text`() {
    assertThat(RepaintArea(2, 5).movedBy(change(2, 0, 3))).isEqualTo(RepaintArea(2, 8))
  }

  @Test
  fun `an insertion in the area grows it`() {
    assertThat(RepaintArea(2, 5).movedBy(change(3, 0, 3))).isEqualTo(RepaintArea(2, 8))
  }

  @Test
  fun `an insertion at the end keeps the area`() {
    assertThat(RepaintArea(2, 5).movedBy(change(5, 0, 3))).isEqualTo(RepaintArea(2, 5))
  }

  @Test
  fun `an insertion at an empty area keeps it before the new text`() {
    assertThat(RepaintArea(3, 3).movedBy(change(3, 0, 3))).isEqualTo(RepaintArea(3, 3))
  }

  @Test
  fun `a deletion over the start moves the start to the deletion`() {
    assertThat(RepaintArea(2, 5).movedBy(change(1, 2, 0))).isEqualTo(RepaintArea(1, 3))
  }

  @Test
  fun `a deletion over the end moves the end to the deletion`() {
    assertThat(RepaintArea(2, 5).movedBy(change(4, 3, 0))).isEqualTo(RepaintArea(2, 4))
  }

  @Test
  fun `a deletion of the whole area leaves an empty area at the deletion`() {
    assertThat(RepaintArea(2, 5).movedBy(change(1, 6, 0))).isEqualTo(RepaintArea(1, 1))
  }

  @Test
  fun `a deletion after the area keeps it`() {
    assertThat(RepaintArea(2, 5).movedBy(change(6, 2, 0))).isEqualTo(RepaintArea(2, 5))
  }

  @Test
  fun `a replacement in the area changes its length`() {
    assertThat(RepaintArea(2, 8).movedBy(change(3, 2, 4))).isEqualTo(RepaintArea(2, 10))
  }

  @Test
  fun `a replacement of the whole text gives an area over the whole new text`() {
    assertThat(RepaintArea(2, 5).movedBy(change(0, 10, 6))).isEqualTo(RepaintArea(0, 6))
  }

  @Test
  fun `NONE stays NONE`() {
    assertThat(RepaintArea.NONE.movedBy(change(0, 0, 3)).isNone()).isTrue()
    assertThat(RepaintArea.NONE.clamp(10).isNone()).isTrue()
  }

  @Test
  fun `clamp keeps the area in the text`() {
    assertThat(RepaintArea(-1, 20).clamp(10)).isEqualTo(RepaintArea(0, 10))
    assertThat(RepaintArea(7, 3).clamp(10)).isEqualTo(RepaintArea(7, 7))
    assertThat(RepaintArea(2, 5).clamp(0)).isEqualTo(RepaintArea(0, 0))
  }
}

/**
 * A change that replaces [oldLength] characters at [offset] with [newLength] characters.
 */
private fun change(offset: Int, oldLength: Int, newLength: Int): DocumentEvent {
  return DocumentEventImpl(
    DocumentImpl("", true),
    offset,
    "x".repeat(oldLength),
    "y".repeat(newLength),
    0,
    false,
    offset,
    oldLength,
    offset,
    0,
  )
}
