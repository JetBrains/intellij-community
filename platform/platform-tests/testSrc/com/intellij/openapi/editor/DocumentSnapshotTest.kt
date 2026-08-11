// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor

import com.intellij.openapi.editor.ex.DocumentSnapshot
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame

@TestApplication
internal class DocumentSnapshotTest {

  @Test
  fun `withText of the own text returns the same snapshot`() {
    val snapshot = snapshot("abc")
    assertSame(snapshot, snapshot.withText(snapshot.text()))
  }

  @Test
  fun `withText of another text returns a snapshot carrying it`() {
    val snapshot = snapshot("abc")
    val otherText = snapshot("xyz").text()
    val updated = snapshot.withText(otherText)
    assertNotSame(snapshot, updated)
    assertSame(otherText, updated.text())
    assertEquals("abc", snapshot.text().string()) // the original snapshot is unaffected
  }

  @Test
  fun `metadata update changing nothing keeps the snapshot instance`() {
    val document = DocumentImpl("abc")
    val before = document.core.snapshot()
    document.modificationStamp = before.text().modStamp()
    assertSame(before, document.core.snapshot()) // relies on withText returning `this` for an unchanged text
  }

  @Test
  fun `frozen document is cached while the snapshot is unchanged`() {
    val document = DocumentImpl("abc")
    assertSame(document.core.frozen(), document.core.frozen())
  }

  @Test
  fun `frozen document is rebuilt once the snapshot changes`() {
    val document = DocumentImpl("abc")
    val before = document.core.frozen()
    document.modificationStamp += 1
    assertNotSame(before, document.core.frozen())
  }

  @Test
  fun `dumpState lists line intervals`() {
    assertEquals("intervals:\n0: 0-2, 1: 3-5", snapshot("ab\ncd").dumpState())
  }

  private fun snapshot(text: String): DocumentSnapshot {
    return DocumentImpl(text).core.snapshot()
  }
}
