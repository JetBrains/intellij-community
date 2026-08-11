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
  fun `withModStamp changing nothing returns the same snapshot`() {
    val snapshot = snapshot("abc")
    assertSame(snapshot, snapshot.withModStamp(snapshot.text().modStamp(), false))
  }

  @Test
  fun `withModStamp returns a snapshot carrying the new stamp`() {
    val snapshot = snapshot("abc")
    val originalStamp = snapshot.text().modStamp()
    val updated = snapshot.withModStamp(42L, true)
    assertNotSame(snapshot, updated)
    assertEquals(42L, updated.text().modStamp())
    assertEquals(snapshot.text().modSequence() + 1, updated.text().modSequence())
    assertSame(snapshot.text().chars(), updated.text().chars()) // the characters are carried over as they are
    assertEquals(originalStamp, snapshot.text().modStamp()) // the original snapshot is unaffected
  }

  @Test
  fun `withClearedLineFlags of an untouched text returns the same snapshot`() {
    val snapshot = snapshot("a\nb\nc")
    assertSame(snapshot, snapshot.withClearedLineFlags(0, Int.MAX_VALUE, IntArray(0)))
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
