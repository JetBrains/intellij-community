// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor

import com.intellij.openapi.application.EDT
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.ex.DocumentNewOps
import com.intellij.openapi.editor.ex.DocumentOp
import com.intellij.openapi.editor.ex.DocumentSnapshot
import com.intellij.openapi.editor.ex.DocumentSputnik
import com.intellij.openapi.editor.ex.DocumentText
import com.intellij.openapi.editor.ex.DocumentTextPatch
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.util.Key
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.text.ImmutableCharSequence
import kotlinx.coroutines.Dispatchers
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

@TestApplication
internal class DocumentSputnikTest {

  @Test
  fun `snapshot has no sputniks initially`() {
    val snapshot = snapshot("abc")
    assertNull(snapshot.sputnik(KEY_1))
  }

  @Test
  fun `sputnik is attached and retrieved by key`() {
    val sputnik = TestSputnik()
    val snapshot = snapshot("abc")
    val newSnapshot = snapshot.applyOp(sputnikOp(KEY_1, sputnik))
    assertSame(sputnik, newSnapshot.sputnik(KEY_1))
    assertNull(newSnapshot.sputnik(KEY_2))
    assertNull(snapshot.sputnik(KEY_1)) // the original snapshot is unaffected
  }

  @Test
  fun `sputniks are retrieved by keys regardless of attach order`() {
    val sputnik1 = TestSputnik()
    val sputnik2 = TestSputnik()
    val sputnik3 = TestSputnik()
    val snapshot = snapshot("abc")
      .applyOp(sputnikOp(KEY_3, sputnik3))
      .applyOp(sputnikOp(KEY_1, sputnik1))
      .applyOp(sputnikOp(KEY_2, sputnik2))
    assertSame(sputnik1, snapshot.sputnik(KEY_1))
    assertSame(sputnik2, snapshot.sputnik(KEY_2))
    assertSame(sputnik3, snapshot.sputnik(KEY_3))
  }

  @Test
  fun `sputnik is replaced by key`() {
    val sputnik1 = TestSputnik()
    val sputnik2 = TestSputnik()
    val other = TestSputnik()
    val snapshot = snapshot("abc")
      .applyOp(sputnikOp(KEY_2, other))
      .applyOp(sputnikOp(KEY_1, sputnik1))
      .applyOp(sputnikOp(KEY_1, sputnik2))
    assertSame(sputnik2, snapshot.sputnik(KEY_1))
    assertSame(other, snapshot.sputnik(KEY_2))
  }

  @Test
  fun `sputnik is removed by key`() {
    val sputnik1 = TestSputnik()
    val sputnik2 = TestSputnik()
    val snapshot = snapshot("abc")
      .applyOp(sputnikOp(KEY_1, sputnik1))
      .applyOp(sputnikOp(KEY_2, sputnik2))
      .applyOp(sputnikOp(KEY_1, null))
    assertNull(snapshot.sputnik(KEY_1))
    assertSame(sputnik2, snapshot.sputnik(KEY_2))
  }

  @Test
  fun `removing the last sputnik works`() {
    val snapshot = snapshot("abc")
      .applyOp(sputnikOp(KEY_1, TestSputnik()))
      .applyOp(sputnikOp(KEY_1, null))
    assertNull(snapshot.sputnik(KEY_1))
  }

  @Test
  fun `attaching the same sputnik is a no-op`() {
    val sputnik = TestSputnik()
    val snapshot = snapshot("abc").applyOp(sputnikOp(KEY_1, sputnik))
    assertSame(snapshot, snapshot.applyOp(sputnikOp(KEY_1, sputnik)))
  }

  @Test
  fun `removing an absent sputnik is a no-op`() {
    val snapshot = snapshot("abc")
    assertSame(snapshot, snapshot.applyOp(sputnikOp(KEY_1, null)))
    val withSputnik = snapshot.applyOp(sputnikOp(KEY_2, TestSputnik()))
    assertSame(withSputnik, withSputnik.applyOp(sputnikOp(KEY_1, null))) // KEY_1 sorts before the attached KEY_2
  }

  @Test
  fun `a key ordered after the attached ones is absent`() {
    // the lookup runs off the end of the sorted keys, unlike `removing an absent sputnik is a no-op`
    val snapshot = snapshot("abc").applyOp(sputnikOp(KEY_1, TestSputnik()))
    assertNull(snapshot.sputnik(KEY_UNAFFECTED)) // KEY_UNAFFECTED sorts after the attached KEY_1
    assertSame(snapshot, snapshot.applyOp(sputnikOp(KEY_UNAFFECTED, null)))
  }

  @Test
  fun `text insertion rebuilds sputnik with change parameters`() {
    val sputnik = TestSputnik()
    val before = snapshot("abc").applyOp(sputnikOp(KEY_1, sputnik))
    val after = insertString(before, offset = 1, fragment = "XY")
    val rebuilt = after.sputnik(KEY_1)!!
    assertEquals(1, rebuilt.rebuildCount)
    assertSame(before.text(), rebuilt.oldText)
    assertSame(after.text(), rebuilt.newText) // the sputnik sees the very text its snapshot carries
    assertEquals("aXYbc", rebuilt.newWholeText.toString())
    assertEquals(1, rebuilt.startOffset)
    assertEquals(1, rebuilt.endOffset)
    assertEquals("XY", rebuilt.newFragment.toString())
    assertSame(sputnik, before.sputnik(KEY_1)) // the old snapshot keeps the old sputnik
  }

  @Test
  fun `text replacement rebuilds sputnik with change parameters`() {
    val before = snapshot("abcdef").applyOp(sputnikOp(KEY_1, TestSputnik()))
    val after = replaceString(before, startOffset = 1, endOffset = 3, fragment = "ZZZ")
    val rebuilt = after.sputnik(KEY_1)!!
    // a range replacement lowers to Delete then Insert, so the sputnik is rebuilt twice: once per op
    assertEquals(2, rebuilt.rebuildCount)
    assertEquals("aZZZdef", rebuilt.newWholeText.toString())
    assertEquals("aZZZdef", rebuilt.newText!!.string())
    assertEquals(1, rebuilt.startOffset)
    assertEquals(1, rebuilt.endOffset)
    assertEquals("ZZZ", rebuilt.newFragment.toString())
  }

  @Test
  fun `unaffected sputnik instance survives text change`() {
    val sputnik = UnaffectedSputnik()
    val before = snapshot("abc").applyOp(sputnikOp(KEY_UNAFFECTED, sputnik))
    val after = insertString(before, offset = 0, fragment = "x")
    assertSame(sputnik, after.sputnik(KEY_UNAFFECTED))
  }

  @Test
  fun `unaffected sputnik ordered after rebuilt ones survives text change`() {
    val affected1 = TestSputnik()
    val affected2 = TestSputnik()
    val unaffected = UnaffectedSputnik()
    val before = snapshot("abc")
      .applyOp(sputnikOp(KEY_1, affected1))
      .applyOp(sputnikOp(KEY_2, affected2))
      .applyOp(sputnikOp(KEY_UNAFFECTED, unaffected))
    val after = insertString(before, offset = 1, fragment = "x")
    assertSame(unaffected, after.sputnik(KEY_UNAFFECTED))
    assertEquals(1, after.sputnik(KEY_1)!!.rebuildCount)
    assertEquals(1, after.sputnik(KEY_2)!!.rebuildCount)
  }

  @Test
  fun `unaffected sputnik ordered before rebuilt one survives text change`() {
    val unaffected = UnaffectedSputnik()
    val affected = TestSputnik()
    val before = snapshot("abc")
      .applyOp(sputnikOp(KEY_UNAFFECTED_LOW, unaffected))
      .applyOp(sputnikOp(KEY_1, affected))
    val after = insertString(before, offset = 1, fragment = "x")
    assertSame(unaffected, after.sputnik(KEY_UNAFFECTED_LOW))
    assertEquals(1, after.sputnik(KEY_1)!!.rebuildCount)
  }

  @Test
  fun `sputnik survives modStamp update`() {
    val sputnik = TestSputnik()
    val snapshot = withModStamp(snapshot("abc").applyOp(sputnikOp(KEY_1, sputnik)))
    assertSame(sputnik, snapshot.sputnik(KEY_1))
    assertEquals(0, sputnik.rebuildCount)
  }

  @Test
  fun `sputnik survives line flags update`() {
    val sputnik = UnaffectedSputnik()
    val before = insertString(snapshot("a\nb\nc").applyOp(sputnikOp(KEY_UNAFFECTED, sputnik)), offset = 2, fragment = "x")
    val op = DocumentNewOps.getInstance().createUnmodifiedLinesOp(0, Int.MAX_VALUE, IntArray(0))
    val after = before.applyOp(op)
    assertSame(sputnik, after.sputnik(KEY_UNAFFECTED))
  }

  @Test
  fun `withMetadata with same text takes sputniks of metadata`() {
    val base = snapshot("abc")
    val withSputnik = base.applyOp(sputnikOp(KEY_1, TestSputnik()))
    val metadata = withModStamp(base) // shares the text characters with `withSputnik`
    val merged = withSputnik.withMetadata(metadata)
    assertEquals(MOD_STAMP, merged.modState().stamp())
    assertNull(merged.sputnik(KEY_1)) // sputniks follow the newest snapshot whose text survives
  }

  @Test
  fun `withMetadata with same text yields the metadata snapshot itself`() {
    val base = snapshot("abc")
    val withSputnik = base.applyOp(sputnikOp(KEY_1, TestSputnik()))
    val metadata = withModStamp(base)
    // unlike the other `with*` methods, withMetadata does not return `this` when nothing else changes
    assertSame(metadata, withSputnik.withMetadata(metadata))
  }

  @Test
  fun `withMetadata with different text drops the other snapshot entirely`() {
    val before = snapshot("abc").applyOp(sputnikOp(KEY_1, TestSputnik()))
    val changed = insertString(before, offset = 0, fragment = "x")
    val metadata = withModStamp(snapshot("abc").applyOp(sputnikOp(KEY_2, TestSputnik())))
    assertSame(changed, changed.withMetadata(metadata))
  }

  @Test
  fun `setSputnik attaches a sputnik visible through the document snapshot`() {
    val document = DocumentImpl("abc")
    val sputnik = TestSputnik()
    val updated = document.core.mutator().setSputnik(KEY_1) { sputnik }
    assertSame(sputnik, updated.sputnik(KEY_1))
    assertSame(updated, document.core.snapshot()) // the returned snapshot is the published one
  }

  @Test
  fun `setSputnik detaches a sputnik`() {
    val document = DocumentImpl("abc")
    document.core.mutator().setSputnik(KEY_1) { TestSputnik() }
    document.core.mutator().setSputnik(KEY_1) { null }
    assertNull(document.core.snapshot().sputnik(KEY_1))
  }

  @Test
  fun `attached sputnik is rebuilt by a document text change`() = runOnEdt {
    val document = DocumentImpl("abc")
    document.core.mutator().setSputnik(KEY_1) { TestSputnik() }
    val textBefore = document.core.snapshot().text()
    WriteCommandAction.runWriteCommandAction(null) {
      document.insertString(1, "XY")
    }
    assertEquals("aXYbc", document.text)
    val rebuilt = document.core.snapshot().sputnik(KEY_1)!!
    assertEquals(1, rebuilt.rebuildCount)
    assertSame(textBefore, rebuilt.oldText)
    assertSame(document.core.snapshot().text(), rebuilt.newText)
    assertEquals("aXYbc", rebuilt.newText!!.string())
  }

  private fun runOnEdt(action: () -> Unit) {
    timeoutRunBlocking(context = Dispatchers.EDT) {
      action()
    }
  }

  private fun snapshot(text: String): DocumentSnapshot {
    return DocumentImpl(text).core.snapshot()
  }

  private fun withModStamp(snapshot: DocumentSnapshot): DocumentSnapshot {
    val op = DocumentNewOps.getInstance().createModStampOp(MOD_STAMP, true)
    return snapshot.applyOp(op)
  }

  private fun insertString(snapshot: DocumentSnapshot, offset: Int, fragment: String): DocumentSnapshot {
    return replaceString(snapshot, offset, offset, fragment)
  }

  private fun replaceString(snapshot: DocumentSnapshot, startOffset: Int, endOffset: Int, fragment: String): DocumentSnapshot {
    return snapshot.applyOps(
      DocumentTextPatch.complex(
        startOffset = startOffset,
        endOffset = endOffset,
        newFragment = fragment,
        newModStamp = snapshot.modState().stamp() + 1,
        clearLineFlags = false,
        originStartOffset = startOffset,
        originEndOffset = endOffset,
      ).toOps()
    )
  }

  private fun <S : DocumentSputnik> sputnikOp(key: Key<S>, sputnik: S?): DocumentOp.SetSputnik {
    return DocumentNewOps.getInstance().createSetSputnikOp(key, sputnik)
  }

  private class TestSputnik(
    val rebuildCount: Int = 0,
    val oldText: DocumentText? = null,
    val newText: DocumentText? = null,
    val newWholeText: ImmutableCharSequence? = null,
    val startOffset: Int = -1,
    val endOffset: Int = -1,
    val newFragment: CharSequence? = null,
  ) : DocumentSputnik {
    override fun applyOp(
      before: DocumentSnapshot,
      after: DocumentSnapshot,
      op: DocumentOp,
    ): DocumentSputnik {
      val startOffset: Int
      val endOffset: Int
      val fragment: CharSequence
      when (op) {
        is DocumentOp.Insert -> {
          startOffset = op.offset()
          endOffset = op.offset()
          fragment = op.fragment()
        }
        is DocumentOp.Delete -> {
          startOffset = op.offset()
          endOffset = op.offset() + op.length()
          fragment = ""
        }
        else -> return this
      }
      val beforeText = before.text()
      val newWholeText = beforeText.chars().replace(startOffset, endOffset, fragment)
      return TestSputnik(
        rebuildCount + 1,
        beforeText,
        after.text(),
        newWholeText,
        startOffset,
        endOffset,
        fragment,
      )
    }
  }

  private class UnaffectedSputnik : DocumentSputnik {
    override fun applyOp(
      before: DocumentSnapshot,
      after: DocumentSnapshot,
      op: DocumentOp,
    ): DocumentSputnik {
      return this
    }
  }

  companion object {
    private const val MOD_STAMP: Long = 42L

    // Key index = creation order, and the sputnik list is sorted by it:
    // KEY_UNAFFECTED_LOW sorts before KEY_1..KEY_3, KEY_UNAFFECTED sorts after them
    private val KEY_UNAFFECTED_LOW: Key<UnaffectedSputnik> = Key.create("test.document.sputnik.unaffected.low")
    private val KEY_1: Key<TestSputnik> = Key.create("test.document.sputnik.1")
    private val KEY_2: Key<TestSputnik> = Key.create("test.document.sputnik.2")
    private val KEY_3: Key<TestSputnik> = Key.create("test.document.sputnik.3")
    private val KEY_UNAFFECTED: Key<UnaffectedSputnik> = Key.create("test.document.sputnik.unaffected")
  }
}
