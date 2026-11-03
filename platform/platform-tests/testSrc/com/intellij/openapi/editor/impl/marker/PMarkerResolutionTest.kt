// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.marker

import com.intellij.openapi.editor.ex.DocumentPatch
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.TextRangeScalarUtil
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@UsePMarkerImplementation
internal class PMarkerResolutionTest {
  private lateinit var markerForHandles: SnapshotRangeMarkerImpl
  private val handles: ArrayList<SnapshotMarkerReference<*>> = ArrayList()

  @BeforeEach
  fun setUp() {
    markerForHandles = DocumentImpl("", true).createRangeMarker(0, 0) as SnapshotRangeMarkerImpl
  }

  @AfterEach
  fun tearDown() {
    if (::markerForHandles.isInitialized) markerForHandles.dispose()
  }

  private fun handle(label: Long): SnapshotMarkerReference<*> {
    while (handles.size <= label) handles.add(SnapshotMarkerReference.create(markerForHandles, false))
    return handles[label.toInt()]
  }


  @Test
  fun `states preserve empty and maximum offsets`() {
    for (range in listOf(TextRange.EMPTY_RANGE, TextRange(2, 4), TextRange(0, Int.MAX_VALUE), TextRange(Int.MAX_VALUE, Int.MAX_VALUE))) {
      val valid = TextRangeScalarUtil.toScalarRange(range)
      val invalid = PMarkerResolution.invalid(TextRangeScalarUtil.toScalarRange(range))
      assertTrue(PMarkerResolution.isValid(valid))
      assertFalse(PMarkerResolution.isValid(invalid))
      for (resolution in longArrayOf(valid, invalid)) {
        assertNotEquals(PMarkerResolution.UNRESOLVED, resolution)
        assertNotEquals(PMarkerResolution.ABSENT, resolution)
        assertEquals(range.startOffset, PMarkerResolution.startOffset(resolution))
        assertEquals(range.endOffset, PMarkerResolution.endOffset(resolution))
        assertEquals(range, PMarkerResolution.toTextRange(resolution))
        assertEquals(resolution == valid, PMarkerResolution.isValid(resolution))
        assertEquals(resolution == invalid, !PMarkerResolution.isValid(resolution))
        assertFalse(PMarkerResolution.isAbsent(resolution))
      }
    }
  }

  @Test
  fun `unresolved is not a marker state`() {
    assertFalse(PMarkerResolution.isValid(PMarkerResolution.UNRESOLVED))
    assertFalse(PMarkerResolution.isAbsent(PMarkerResolution.UNRESOLVED))
  }

  @Test
  fun `absent is a distinct sentinel without offsets`() {
    assertEquals(-2L, PMarkerResolution.ABSENT)
    assertNotEquals(PMarkerResolution.UNRESOLVED, PMarkerResolution.ABSENT)
    assertTrue(PMarkerResolution.isAbsent(PMarkerResolution.ABSENT))
    assertFalse(PMarkerResolution.isValid(PMarkerResolution.ABSENT))
    assertEquals(PMarkerResolution.ABSENT, PMarkerRoot.empty().resolve(handle(1)))
    assertFalse(PMarkerRoot.empty().isValid(handle(1)))
  }

  @Test
  fun `invalid offsets are rejected`() {
    for ([start, end] in listOf(-1 to 0, 2 to 1)) {
      assertThrows(IllegalArgumentException::class.java) { PMarkerResolution.invalid(TextRangeScalarUtil.toScalarRange(start, end)) }
    }
  }

  @Test
  fun `removed markers stay absent after a merge`() {
    val spec = MarkerSpec.create(isGreedyToLeft = false, isGreedyToRight = false)
    val root = PMarkerRoot.empty().insert(handle(1), 2, 4, spec, flavorFlags = 0)
    val removed = root.remove(handle(1))

    assertTrue(root.isValid(handle(1)))
    assertFalse(removed.isValid(handle(1)))
    assertEquals(PMarkerResolution.ABSENT, removed.resolve(handle(1)))
    assertEquals(TextRangeScalarUtil.toScalarRange(2, 4), root.resolve(handle(1)))
    assertSame(removed, removed.remove(handle(1)))
    assertEquals(PMarkerResolution.ABSENT, removed.mergeValidMarkersFrom(root).resolve(handle(1)))
    assertThrows(AssertionError::class.java) { removed.insert(handle(1), 3, 6, spec, flavorFlags = 0) }
  }

  @Test
  fun `invalidation reasons remain in their roots`() {
    val document = DocumentImpl("abcdef", true)
    val reason = MarkerTransformError.INVALIDATED_BY_EDIT.message
    val spec = MarkerSpec.create(isGreedyToLeft = false, isGreedyToRight = false)
    val policy = MarkerPolicy { _, _, _, _ ->
      MarkerTransformResult(MarkerTransformError.INVALIDATED_BY_EDIT)
    }
    val root = PMarkerRoot.empty().insert(handle(1), 2, 4, spec, flavorFlags = 0, policy = policy)
    val before = document.core.snapshot().text()
    val patch = DocumentPatch.simple(2, 3, "x", newModStamp = 1, clearLineFlags = false)
    val after = before.replace(patch.startOffset(), patch.endOffset(), patch.newFragment())
    val edited = root.applyPatch(patch, before, after)

    assertTrue(root.isValid(handle(1)))
    assertFalse(edited.isValid(handle(1)))
    assertFalse(edited.isValid(handle(2)))
    assertEquals(PMarkerResolution.invalid(TextRangeScalarUtil.toScalarRange(2, 4)), edited.resolve(handle(1)))
    assertEquals(reason, edited.invalidationReason(handle(1)))
    assertNull(root.invalidationReason(handle(1)))
    assertNull(edited.invalidationReason(handle(2)))
    val removed = edited.remove(handle(1))
    assertNull(removed.invalidationReason(handle(1)))
    assertEquals(PMarkerResolution.ABSENT, removed.resolve(handle(1)))
    assertEquals(PMarkerResolution.invalid(TextRangeScalarUtil.toScalarRange(2, 4)), edited.resolve(handle(1)))
  }
}
