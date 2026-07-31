// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor

import com.intellij.openapi.editor.ex.DocumentAspect
import com.intellij.openapi.editor.ex.DocumentSnapshot
import com.intellij.openapi.editor.ex.DocumentTextPatch
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.util.Key
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.text.ImmutableCharSequence
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
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
    assertSame(withAspect, withAspect.withAspect(KEY_1, null))
  }

  @Test
  fun `text insertion rebuilds aspect with change parameters`() {
    val aspect = TestAspect()
    val before = snapshot("abc").withAspect(KEY_1, aspect)
    val after = insertString(before, offset = 1, fragment = "XY")
    val rebuilt = after.aspect(KEY_1)!!
    assertEquals(1, rebuilt.rebuildCount)
    assertSame(before, rebuilt.oldSnapshot)
    assertEquals("aXYbc", rebuilt.newWholeText.toString())
    assertEquals(1, rebuilt.startOffset)
    assertEquals(1, rebuilt.endOffset)
    assertEquals("XY", rebuilt.newFragment.toString())
    assertEquals(before.modStamp() + 1, rebuilt.newModStamp)
    assertSame(aspect, before.aspect(KEY_1)) // the old snapshot keeps the old aspect
  }

  @Test
  fun `text replacement rebuilds aspect with change parameters`() {
    val before = snapshot("abcdef").withAspect(KEY_1, TestAspect())
    val after = replaceString(before, startOffset = 1, endOffset = 3, fragment = "ZZZ")
    val rebuilt = after.aspect(KEY_1)!!
    assertEquals("aZZZdef", rebuilt.newWholeText.toString())
    assertEquals(1, rebuilt.startOffset)
    assertEquals(3, rebuilt.endOffset)
    assertEquals("ZZZ", rebuilt.newFragment.toString())
  }

  @Test
  fun `aspect receives origin range of a narrowed change`() {
    val before = snapshot("abcdef").withAspect(KEY_1, TestAspect())
    val after = before.withText(
      DocumentTextPatch.complex(
        startOffset = 2,
        endOffset = 3,
        newFragment = "Z",
        newModStamp = before.modStamp() + 1,
        clearLineFlags = false,
        clearModTree = false,
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
    val snapshot = snapshot("abc")
      .withAspect(KEY_1, aspect)
      .withModStamp(42L, true)
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
    val metadata = base.withModStamp(42L, true) // shares the text instance with `withAspect`
    val merged = withAspect.withMetadata(metadata)
    assertEquals(42L, merged.modStamp())
    assertNull(merged.aspect(KEY_1)) // aspects follow the newest snapshot whose text survives
  }

  @Test
  fun `withMetadata with different text keeps aspects of this snapshot`() {
    val before = snapshot("abc").withAspect(KEY_1, TestAspect())
    val changed = insertString(before, offset = 0, fragment = "x")
    val metadata = snapshot("abc")
      .withAspect(KEY_2, TestAspect())
      .withModStamp(42L, true)
    val merged = changed.withMetadata(metadata)
    assertEquals(42L, merged.modStamp())
    val kept = merged.aspect(KEY_1)
    assertNotNull(kept) // this text survives, so this aspects survive
    assertSame(changed.aspect(KEY_1), kept)
    assertNull(merged.aspect(KEY_2)) // metadata aspects correspond to its discarded text
  }

  private fun snapshot(text: String): DocumentSnapshot {
    return DocumentImpl(text).core.snapshot()
  }

  private fun insertString(snapshot: DocumentSnapshot, offset: Int, fragment: String): DocumentSnapshot {
    return replaceString(snapshot, offset, offset, fragment)
  }

  private fun replaceString(snapshot: DocumentSnapshot, startOffset: Int, endOffset: Int, fragment: String): DocumentSnapshot {
    return snapshot.withText(
      DocumentTextPatch.complex(
        startOffset = startOffset,
        endOffset = endOffset,
        newFragment = fragment,
        newModStamp = snapshot.modStamp() + 1,
        clearLineFlags = false,
        clearModTree = false,
        originStartOffset = startOffset,
        originEndOffset = endOffset,
      )
    )
  }

  private class TestAspect(
    val rebuildCount: Int = 0,
    val oldSnapshot: DocumentSnapshot? = null,
    val newWholeText: ImmutableCharSequence? = null,
    val startOffset: Int = -1,
    val endOffset: Int = -1,
    val newFragment: CharSequence? = null,
    val newModStamp: Long = -1L,
    val originStartOffset: Int = -1,
    val originEndOffset: Int = -1,
  ) : DocumentAspect {
    override fun withText(
      beforeText: DocumentSnapshot,
      patch: DocumentTextPatch,
    ): DocumentAspect {
      val newWholeText = beforeText.text().replace(patch.startOffset(), patch.endOffset(), patch.newFragment())
      return TestAspect(
        rebuildCount + 1,
        beforeText,
        newWholeText,
        patch.startOffset(),
        patch.endOffset(),
        patch.newFragment(),
        patch.newModStamp(),
        patch.originStartOffset(),
        patch.originEndOffset(),
      )
    }
  }

  private class UnaffectedAspect : DocumentAspect {
    override fun withText(
      beforeText: DocumentSnapshot,
      patch: DocumentTextPatch,
    ): DocumentAspect {
      return this
    }
  }

  companion object {
    // Key index = creation order, and the aspect list is sorted by it:
    // KEY_UNAFFECTED_LOW sorts before KEY_1..KEY_3, KEY_UNAFFECTED sorts after them
    private val KEY_UNAFFECTED_LOW: Key<UnaffectedAspect> = Key.create("test.document.aspect.unaffected.low")
    private val KEY_1: Key<TestAspect> = Key.create("test.document.aspect.1")
    private val KEY_2: Key<TestAspect> = Key.create("test.document.aspect.2")
    private val KEY_3: Key<TestAspect> = Key.create("test.document.aspect.3")
    private val KEY_UNAFFECTED: Key<UnaffectedAspect> = Key.create("test.document.aspect.unaffected")
  }
}
