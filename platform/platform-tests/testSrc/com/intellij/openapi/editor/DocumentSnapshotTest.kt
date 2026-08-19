// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor

import com.intellij.openapi.editor.ex.DocumentNewOps
import com.intellij.openapi.editor.ex.DocumentOp
import com.intellij.openapi.editor.ex.DocumentSnapshot
import com.intellij.openapi.editor.ex.DocumentTextPatch
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertSame

@TestApplication
internal class DocumentSnapshotTest {

  @Test
  fun `withModStamp changing nothing returns the same snapshot`() {
    val snapshot = snapshot("abc")
    assertSame(snapshot, snapshot.applyOp(modStampOp(snapshot.modState().stamp(), false)))
  }

  @Test
  fun `withModStamp returns a snapshot carrying the new stamp`() {
    val snapshot = snapshot("abc")
    val originalStamp = snapshot.modState().stamp()
    val updated = snapshot.applyOp(modStampOp(42L, true))
    assertNotSame(snapshot, updated)
    assertEquals(42L, updated.modState().stamp())
    assertEquals(snapshot.modState().sequence() + 1, updated.modState().sequence())
    assertSame(snapshot.text().chars(), updated.text().chars()) // the characters are carried over as they are
    assertEquals(originalStamp, snapshot.modState().stamp()) // the original snapshot is unaffected
  }

  @Test
  fun `withClearedLineFlags of an untouched text returns the same snapshot`() {
    val snapshot = snapshot("a\nb\nc")
    val newOps = DocumentNewOps.getInstance()
    val op = newOps.createUnmodifiedLinesOp(0, Int.MAX_VALUE, IntArray(0))
    assertSame(snapshot, snapshot.applyOp(op))
  }

  @Test
  fun `metadata update changing nothing keeps the snapshot instance`() {
    val document = DocumentImpl("abc")
    val before = document.core.snapshot()
    document.modificationStamp = before.modState().stamp()
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

  @Test
  fun `applyOp after a line query keeps the resulting line count correct`() {
    val snapshot = snapshot("a\nb")
    snapshot.text().lineCount() // force lineSet computation before the insert
    val patched = insertString(snapshot, fragment = "xy")
    assertEquals("xya\nb", patched.text().string())
    assertEquals(2, patched.text().lineCount())
  }

  @Test
  fun `withMetadata with a different, longer text keeps line-modification tracking consistent`() {
    val patched = insertString(snapshot("a"), fragment = "z") // builds modState().lineSet for a 1-line text
    val longText = snapshot("a\nb\nc\nd\ne") // unrelated, never patched, 5 real lines
    val merged = longText.withMetadata(patched)
    assertEquals(5, merged.text().lineCount()) // the kept text is unaffected by the metadata merge
    assertFalse(merged.modState().isLineModified(3)) // must not throw for a line valid in the kept (longer) text
  }

  @Test
  fun `fresh unrelated documents do not share line-modification state`() {
    insertString(snapshot("z"), fragment = "y") // builds a fresh DocumentModStateImpl's lineSet
    val unrelated = snapshot("a\nb\nc\nd\ne") // a different, never-edited document, 5 real lines
    assertFalse(unrelated.modState().isLineModified(3)) // must not throw or reflect the other document's state
  }

  private fun insertString(snapshot: DocumentSnapshot, fragment: String): DocumentSnapshot {
    return snapshot.applyOps(
      DocumentTextPatch.simple(
        startOffset = 0,
        endOffset = 0,
        newFragment = fragment,
        newModStamp = snapshot.modState().stamp() + 1,
        clearLineFlags = false,
      ).toOps()
    )
  }

  private fun modStampOp(stamp: Long, incSequence: Boolean): DocumentOp.ModStamp {
    return DocumentNewOps.getInstance().createModStampOp(stamp, incSequence)
  }

  private fun snapshot(text: String): DocumentSnapshot {
    return DocumentImpl(text).core.snapshot()
  }
}
