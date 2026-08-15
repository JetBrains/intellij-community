// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor

import com.intellij.openapi.application.EDT
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.ex.DocumentAspect
import com.intellij.openapi.editor.ex.DocumentSnapshot
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
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame

@TestApplication
internal class DocumentAspectTest {

  @Test
  fun `snapshot has no aspects initially`() {
    val snapshot = snapshot("abc")
    assertNull(snapshot.aspect(KEY_1))
  }

  @Test
  fun `aspect is attached and retrieved by key`() {
    val aspect = TestAspect()
    val snapshot = snapshot("abc")
    val newSnapshot = snapshot.withAspect(KEY_1, aspect)
    assertSame(aspect, newSnapshot.aspect(KEY_1))
    assertNull(newSnapshot.aspect(KEY_2))
    assertNull(snapshot.aspect(KEY_1)) // the original snapshot is unaffected
  }

  @Test
  fun `aspects are retrieved by keys regardless of attach order`() {
    val aspect1 = TestAspect()
    val aspect2 = TestAspect()
    val aspect3 = TestAspect()
    val snapshot = snapshot("abc")
      .withAspect(KEY_3, aspect3)
      .withAspect(KEY_1, aspect1)
      .withAspect(KEY_2, aspect2)
    assertSame(aspect1, snapshot.aspect(KEY_1))
    assertSame(aspect2, snapshot.aspect(KEY_2))
    assertSame(aspect3, snapshot.aspect(KEY_3))
  }

  @Test
  fun `aspect is replaced by key`() {
    val aspect1 = TestAspect()
    val aspect2 = TestAspect()
    val other = TestAspect()
    val snapshot = snapshot("abc")
      .withAspect(KEY_2, other)
      .withAspect(KEY_1, aspect1)
      .withAspect(KEY_1, aspect2)
    assertSame(aspect2, snapshot.aspect(KEY_1))
    assertSame(other, snapshot.aspect(KEY_2))
  }

  @Test
  fun `aspect is removed by key`() {
    val aspect1 = TestAspect()
    val aspect2 = TestAspect()
    val snapshot = snapshot("abc")
      .withAspect(KEY_1, aspect1)
      .withAspect(KEY_2, aspect2)
      .withAspect(KEY_1, null)
    assertNull(snapshot.aspect(KEY_1))
    assertSame(aspect2, snapshot.aspect(KEY_2))
  }

  @Test
  fun `removing the last aspect works`() {
    val snapshot = snapshot("abc")
      .withAspect(KEY_1, TestAspect())
      .withAspect(KEY_1, null)
    assertNull(snapshot.aspect(KEY_1))
  }

  @Test
  fun `attaching the same aspect is a no-op`() {
    val aspect = TestAspect()
    val snapshot = snapshot("abc").withAspect(KEY_1, aspect)
    assertSame(snapshot, snapshot.withAspect(KEY_1, aspect))
  }

  @Test
  fun `removing an absent aspect is a no-op`() {
    val snapshot = snapshot("abc")
    assertSame(snapshot, snapshot.withAspect(KEY_1, null))
    val withAspect = snapshot.withAspect(KEY_2, TestAspect())
    assertSame(withAspect, withAspect.withAspect(KEY_1, null)) // KEY_1 sorts before the attached KEY_2
  }

  @Test
  fun `a key ordered after the attached ones is absent`() {
    // the lookup runs off the end of the sorted keys, unlike `removing an absent aspect is a no-op`
    val snapshot = snapshot("abc").withAspect(KEY_1, TestAspect())
    assertNull(snapshot.aspect(KEY_UNAFFECTED)) // KEY_UNAFFECTED sorts after the attached KEY_1
    assertSame(snapshot, snapshot.withAspect(KEY_UNAFFECTED, null))
  }

  @Test
  fun `text insertion rebuilds aspect with change parameters`() {
    val aspect = TestAspect()
    val before = snapshot("abc").withAspect(KEY_1, aspect)
    val after = insertString(before, offset = 1, fragment = "XY")
    val rebuilt = after.aspect(KEY_1)!!
    assertEquals(1, rebuilt.rebuildCount)
    assertSame(before.text(), rebuilt.oldText)
    assertSame(after.text(), rebuilt.newText) // the aspect sees the very text its snapshot carries
    assertEquals("aXYbc", rebuilt.newWholeText.toString())
    assertEquals(1, rebuilt.startOffset)
    assertEquals(1, rebuilt.endOffset)
    assertEquals("XY", rebuilt.newFragment.toString())
    assertEquals(before.modState().stamp() + 1, rebuilt.newModStamp)
    assertSame(aspect, before.aspect(KEY_1)) // the old snapshot keeps the old aspect
  }

  @Test
  fun `text replacement rebuilds aspect with change parameters`() {
    val before = snapshot("abcdef").withAspect(KEY_1, TestAspect())
    val after = replaceString(before, startOffset = 1, endOffset = 3, fragment = "ZZZ")
    val rebuilt = after.aspect(KEY_1)!!
    assertEquals("aZZZdef", rebuilt.newWholeText.toString())
    assertEquals("aZZZdef", rebuilt.newText!!.string())
    assertEquals(1, rebuilt.startOffset)
    assertEquals(3, rebuilt.endOffset)
    assertEquals("ZZZ", rebuilt.newFragment.toString())
  }

  @Test
  fun `aspect receives origin range of a narrowed change`() {
    val before = snapshot("abcdef").withAspect(KEY_1, TestAspect())
    val after = before.withPatch(
      DocumentTextPatch.complex(
        startOffset = 2,
        endOffset = 3,
        newFragment = "Z",
        newModStamp = before.modState().stamp() + 1,
        clearLineFlags = false,
        originStartOffset = 1,
        originEndOffset = 4,
      )
    )
    val rebuilt = after.aspect(KEY_1)!!
    assertEquals(2, rebuilt.startOffset)
    assertEquals(3, rebuilt.endOffset)
    assertEquals(1, rebuilt.originStartOffset)
    assertEquals(4, rebuilt.originEndOffset)
  }

  @Test
  fun `unaffected aspect instance survives text change`() {
    val aspect = UnaffectedAspect()
    val before = snapshot("abc").withAspect(KEY_UNAFFECTED, aspect)
    val after = insertString(before, offset = 0, fragment = "x")
    assertSame(aspect, after.aspect(KEY_UNAFFECTED))
  }

  @Test
  fun `unaffected aspect ordered after rebuilt ones survives text change`() {
    val affected1 = TestAspect()
    val affected2 = TestAspect()
    val unaffected = UnaffectedAspect()
    val before = snapshot("abc")
      .withAspect(KEY_1, affected1)
      .withAspect(KEY_2, affected2)
      .withAspect(KEY_UNAFFECTED, unaffected)
    val after = insertString(before, offset = 1, fragment = "x")
    assertSame(unaffected, after.aspect(KEY_UNAFFECTED))
    assertEquals(1, after.aspect(KEY_1)!!.rebuildCount)
    assertEquals(1, after.aspect(KEY_2)!!.rebuildCount)
  }

  @Test
  fun `unaffected aspect ordered before rebuilt one survives text change`() {
    val unaffected = UnaffectedAspect()
    val affected = TestAspect()
    val before = snapshot("abc")
      .withAspect(KEY_UNAFFECTED_LOW, unaffected)
      .withAspect(KEY_1, affected)
    val after = insertString(before, offset = 1, fragment = "x")
    assertSame(unaffected, after.aspect(KEY_UNAFFECTED_LOW))
    assertEquals(1, after.aspect(KEY_1)!!.rebuildCount)
  }

  @Test
  fun `aspect survives modStamp update`() {
    val aspect = TestAspect()
    val snapshot = withModStamp(snapshot("abc").withAspect(KEY_1, aspect))
    assertSame(aspect, snapshot.aspect(KEY_1))
    assertEquals(0, aspect.rebuildCount)
  }

  @Test
  fun `aspect survives line flags update`() {
    val aspect = UnaffectedAspect()
    val before = insertString(snapshot("a\nb\nc").withAspect(KEY_UNAFFECTED, aspect), offset = 2, fragment = "x")
    val after = before.withClearedLineFlags(0, Int.MAX_VALUE, IntArray(0))
    assertSame(aspect, after.aspect(KEY_UNAFFECTED))
  }

  @Test
  fun `withMetadata with same text takes aspects of metadata`() {
    val base = snapshot("abc")
    val withAspect = base.withAspect(KEY_1, TestAspect())
    val metadata = withModStamp(base) // shares the text characters with `withAspect`
    val merged = withAspect.withMetadata(metadata)
    assertEquals(MOD_STAMP, merged.modState().stamp())
    assertNull(merged.aspect(KEY_1)) // aspects follow the newest snapshot whose text survives
  }

  @Test
  fun `withMetadata with same text yields the metadata snapshot itself`() {
    val base = snapshot("abc")
    val withAspect = base.withAspect(KEY_1, TestAspect())
    val metadata = withModStamp(base)
    // unlike the other `with*` methods, withMetadata does not return `this` when nothing else changes
    assertSame(metadata, withAspect.withMetadata(metadata))
  }

  @Test
  fun `withMetadata with different text keeps aspects of this snapshot`() {
    val before = snapshot("abc").withAspect(KEY_1, TestAspect())
    val changed = insertString(before, offset = 0, fragment = "x")
    val metadata = withModStamp(snapshot("abc").withAspect(KEY_2, TestAspect()))
    val merged = changed.withMetadata(metadata)
    assertEquals(MOD_STAMP, merged.modState().stamp())
    val kept = merged.aspect(KEY_1)
    assertNotNull(kept) // this text survives, so this aspects survive
    assertSame(changed.aspect(KEY_1), kept)
    assertNull(merged.aspect(KEY_2)) // metadata aspects correspond to its discarded text
  }

  @Test
  fun `updateSnapshotAndGet attaches an aspect visible through the document snapshot`() {
    val document = DocumentImpl("abc")
    val aspect = TestAspect()
    val updated = document.core.mutator().updateSnapshotAndGet { it.withAspect(KEY_1, aspect) }
    assertSame(aspect, updated.aspect(KEY_1))
    assertSame(updated, document.core.snapshot()) // the returned snapshot is the published one
  }

  @Test
  fun `updateSnapshotAndGet detaches an aspect`() {
    val document = DocumentImpl("abc")
    document.core.mutator().updateSnapshotAndGet { it.withAspect(KEY_1, TestAspect()) }
    document.core.mutator().updateSnapshotAndGet { it.withAspect(KEY_1, null) }
    assertNull(document.core.snapshot().aspect(KEY_1))
  }

  @Test
  fun `updateSnapshotAndGet rejects a change of the characters`() {
    val document = DocumentImpl("abc")
    val mutator = document.core.mutator()
    val patch = DocumentTextPatch.simple(
      startOffset = 0,
      endOffset = 0,
      newFragment = "x",
      newModStamp = document.core.snapshot().modState().stamp() + 1,
      clearLineFlags = false,
    )
    val snapshotBefore = document.core.snapshot()
    // a text change published this way would fire no DocumentListener
    assertFailsWith<IllegalArgumentException> {
      mutator.updateSnapshotAndGet { it.withPatch(patch) }
    }
    assertSame(snapshotBefore, document.core.snapshot()) // nothing was published
    assertEquals("abc", document.text)
  }

  @Test
  fun `attached aspect is rebuilt by a document text change`() = runOnEdt {
    val document = DocumentImpl("abc")
    document.core.mutator().updateSnapshotAndGet { it.withAspect(KEY_1, TestAspect()) }
    val textBefore = document.core.snapshot().text()
    WriteCommandAction.runWriteCommandAction(null) {
      document.insertString(1, "XY")
    }
    assertEquals("aXYbc", document.text)
    val rebuilt = document.core.snapshot().aspect(KEY_1)!!
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
    return snapshot.withModStamp(MOD_STAMP, true)
  }

  private fun insertString(snapshot: DocumentSnapshot, offset: Int, fragment: String): DocumentSnapshot {
    return replaceString(snapshot, offset, offset, fragment)
  }

  private fun replaceString(snapshot: DocumentSnapshot, startOffset: Int, endOffset: Int, fragment: String): DocumentSnapshot {
    return snapshot.withPatch(
      DocumentTextPatch.complex(
        startOffset = startOffset,
        endOffset = endOffset,
        newFragment = fragment,
        newModStamp = snapshot.modState().stamp() + 1,
        clearLineFlags = false,
        originStartOffset = startOffset,
        originEndOffset = endOffset,
      )
    )
  }

  private class TestAspect(
    val rebuildCount: Int = 0,
    val oldText: DocumentText? = null,
    val newText: DocumentText? = null,
    val newWholeText: ImmutableCharSequence? = null,
    val startOffset: Int = -1,
    val endOffset: Int = -1,
    val newFragment: CharSequence? = null,
    val newModStamp: Long = -1L,
    val originStartOffset: Int = -1,
    val originEndOffset: Int = -1,
  ) : DocumentAspect {
    override fun withTextChange(
      before: DocumentText,
      after: DocumentText,
      diff: DocumentTextPatch,
    ): DocumentAspect {
      val newWholeText = before.chars().replace(diff.startOffset(), diff.endOffset(), diff.newFragment())
      return TestAspect(
        rebuildCount + 1,
        before,
        after,
        newWholeText,
        diff.startOffset(),
        diff.endOffset(),
        diff.newFragment(),
        diff.newModStamp(),
        diff.originStartOffset(),
        diff.originEndOffset(),
      )
    }
  }

  private class UnaffectedAspect : DocumentAspect {
    override fun withTextChange(
      before: DocumentText,
      after: DocumentText,
      diff: DocumentTextPatch,
    ): DocumentAspect {
      return this
    }
  }

  companion object {
    private const val MOD_STAMP: Long = 42L

    // Key index = creation order, and the aspect list is sorted by it:
    // KEY_UNAFFECTED_LOW sorts before KEY_1..KEY_3, KEY_UNAFFECTED sorts after them
    private val KEY_UNAFFECTED_LOW: Key<UnaffectedAspect> = Key.create("test.document.aspect.unaffected.low")
    private val KEY_1: Key<TestAspect> = Key.create("test.document.aspect.1")
    private val KEY_2: Key<TestAspect> = Key.create("test.document.aspect.2")
    private val KEY_3: Key<TestAspect> = Key.create("test.document.aspect.3")
    private val KEY_UNAFFECTED: Key<UnaffectedAspect> = Key.create("test.document.aspect.unaffected")
  }
}
