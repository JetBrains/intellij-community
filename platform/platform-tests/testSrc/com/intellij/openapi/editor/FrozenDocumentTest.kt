// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor

import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame

@TestApplication
internal class FrozenDocumentTest {

  @Test
  fun `frozen document is cached while the snapshot is unchanged`() {
    val document = DocumentImpl("abc")
    assertSame(document.core.frozen(), document.core.frozen())
  }

  @Test
  fun `frozen document is rebuilt once the stamp changes`() {
    val document = DocumentImpl("abc")
    val before = document.core.frozen()
    document.modificationStamp += 1
    val after = document.core.frozen()
    assertNotSame(before, after)
    assertEquals(document.modificationStamp, after.modificationStamp)
  }

  @Test
  fun `frozen document is rebuilt when the text changes under the same stamp`() {
    val document = DocumentImpl("abc", true)
    val before = document.core.frozen()
    document.replaceText("xyz", before.modificationStamp)
    val after = document.core.frozen()
    assertNotSame(before, after)
    assertEquals("xyz", after.text)
    assertEquals(before.modificationStamp, after.modificationStamp)
  }

  @Test
  fun `frozen document stays cached when only the line flags change`() {
    val document = DocumentImpl("a\nb", true)
    document.insertString(0, "x")
    val before = document.core.frozen()
    val snapshot = document.core.snapshot()
    document.clearLineModificationFlags()
    // The snapshot changes, but the text and the stamp that a frozen document holds do not.
    assertNotSame(snapshot, document.core.snapshot())
    assertSame(before, document.core.frozen())
  }

  @Test
  fun `applyEvent gives the text after the event and the stamp 0`() {
    val document = DocumentImpl("abc", true)
    val frozen = document.freeze()
    val events = ArrayList<DocumentEvent>()
    document.addDocumentListener(object : DocumentListener {
      override fun documentChanged(event: DocumentEvent) {
        events.add(event)
      }
    })
    document.replaceString(1, 2, "XY")
    val applied = frozen.applyEvent(events.single())
    assertEquals("aXYc", applied.text)
    assertEquals(0L, applied.modificationStamp)
    // The frozen document before the event stays as it was.
    assertEquals("abc", frozen.text)
    assertEquals(document.core.frozen().text, applied.text)
  }
}
