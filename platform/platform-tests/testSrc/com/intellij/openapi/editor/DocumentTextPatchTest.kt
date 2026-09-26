// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor

import com.intellij.openapi.editor.ex.DocumentTextPatch
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

internal class DocumentTextPatchTest {

  @Test
  fun `simple patch is printed without the origin and move fields`() {
    val patch = DocumentTextPatch.simple(
      startOffset = 1,
      endOffset = 2,
      newFragment = "x",
      newModStamp = 7L,
      clearLineFlags = false,
    )
    assertEquals(
      "SimpleTextPatch(startOffset=1, endOffset=2, newFragment.length=1, newModStamp=7, clearLineFlags=false)",
      patch.toString(),
    )
  }

  @Test
  fun `complex patch is printed with the fields differing from the applied range`() {
    val patch = DocumentTextPatch.complex(
      startOffset = 5,
      endOffset = 6,
      newFragment = "y",
      newModStamp = 8L,
      clearLineFlags = true,
      originStartOffset = 3,
      originEndOffset = 6, // same as endOffset, so it is omitted
      moveOffset = 4,
    )
    assertEquals(
      "ComplexTextPatch(startOffset=5, endOffset=6, newFragment.length=1" +
      ", originStartOffset=3, moveOffset=4, newModStamp=8, clearLineFlags=true)",
      patch.toString(),
    )
  }

  @Test
  fun `patch freezes a mutable new fragment`() {
    val fragment = StringBuilder("new")
    val patch = DocumentTextPatch.simple(
      startOffset = 1,
      endOffset = 2,
      newFragment = fragment,
      newModStamp = 9L,
      clearLineFlags = false,
    )

    fragment.replace(0, fragment.length, "changed")

    assertEquals("new", patch.newFragment().toString())
  }
}
