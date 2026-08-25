// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.editor.impl.event.DocumentEventImpl
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

internal class DocumentLineDiffTest {
  @Test
  fun `strict translation rejects replaced lines while regular translation keeps their position`() {
    val afterText = "a\nnew\nb"
    val event = event(oldFragment = "a\nold\nb", newFragment = afterText, afterText = afterText)

    assertEquals(-1, event.translateLineViaDiffStrict(1))
    assertEquals(1, event.translateLineViaDiff(1))
    assertEquals(2, event.translateLineViaDiffStrict(2))
  }

  @Test
  fun `computed changes are reused`() {
    val oldFragment = StringBuilder("a\nb")
    val afterText = "x\na\nb"
    val event = event(oldFragment, newFragment = afterText, afterText = afterText)

    assertEquals(2, event.translateLineViaDiffStrict(1))
    oldFragment.setLength(0)
    assertEquals(2, event.translateLineViaDiffStrict(1))
  }

  private fun event(oldFragment: CharSequence, newFragment: CharSequence, afterText: String): DocumentEventImpl {
    val document = DocumentImpl(afterText, true)
    return DocumentEventImpl(
      document,
      0,
      oldFragment,
      newFragment,
      document.modificationStamp,
      false,
      0,
      oldFragment.length,
      0,
      oldFragment.length,
    )
  }
}
