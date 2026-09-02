// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.editor.ex.DocumentTextPatch
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.editor.impl.marker.PMarker
import com.intellij.openapi.editor.impl.marker.SnapshotRangeMarkerImpl
import com.intellij.openapi.editor.impl.marker.UsePMarkerImplementation
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.ref.GCUtil
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.lang.ref.WeakReference

@TestApplication
@UsePMarkerImplementation
class SnapshotGuardedBlockTest {
  @Test
  @UsePMarkerImplementation(false)
  fun `disabled snapshot marker implementation uses legacy guard`() {
    val document = DocumentImpl("abcdef", true)

    val guard = document.createGuardedBlock(2, 4)

    Assertions.assertThat(guard).isExactlyInstanceOf(GuardedBlock::class.java)
  }

  @Test
  fun `guard uses persistent snapshot policy and guard flavor`() {
    val document = DocumentImpl("one\ntarget\nlast", true)
    val initialSnapshot = document.core.snapshot()

    val guard = document.createGuardedBlock(4, 10)

    Assertions.assertThat(guard).isInstanceOf(SnapshotRangeMarkerImpl::class.java)
    Assertions.assertThat(document.guardedBlocks).containsExactly(guard)
    Assertions.assertThat(document.getRangeGuard(5, 6)).isSameAs(guard)

    val snapshotGuard = guard as PMarker
    Assertions.assertThat(snapshotGuard.resolve(initialSnapshot))
      .extracting("startOffset", "endOffset").containsExactly(4, 10)

    val insertedBefore = initialSnapshot.applyOp(textPatch(0, 0, "x\n"))
    Assertions.assertThat(snapshotGuard.resolve(insertedBefore))
      .extracting("startOffset", "endOffset").containsExactly(6, 12)

    val replaced = initialSnapshot.applyOp(textPatch(0, initialSnapshot.text().length(), "prefix\none\ntarget\nlast"))
    Assertions.assertThat(snapshotGuard.resolve(replaced))
      .extracting("startOffset", "endOffset").containsExactly(11, 17)

    val deleted = initialSnapshot.applyOp(textPatch(3, 11, ""))
    Assertions.assertThat(snapshotGuard.resolve(deleted).isValid).isFalse()

    document.removeGuardedBlock(guard)
    Assertions.assertThat(guard.isValid).isFalse()
    Assertions.assertThat(document.guardedBlocks).isEmpty()
  }

  @ParameterizedTest(name = "query [{0}, {1}] returns the guard: {2}")
  @CsvSource(
    "0, 6, true",
    "0, 3, true",
    "4, 6, true",
    "3, 4, true",
    "2, 3, true",
    "4, 5, true",
    "2, 5, true",
    "3, 3, true",
    "0, 1, false",
    "6, 7, false",
    "0, 2, false",
    "5, 6, false",
    "2, 2, false",
    "5, 5, false",
  )
  fun `guard lookup handles different boundaries`(startOffset: Int, endOffset: Int, returnsGuard: Boolean) {
    val document = DocumentImpl("abcdef", true)
    val guard = document.createGuardedBlock(2, 5)

    val expected = if (returnsGuard) guard else null
    Assertions.assertThat(document.getRangeGuard(startOffset, endOffset)).isSameAs(expected)
  }

  @Test
  fun `guard lookup applies greediness to non-strict tree candidates`() {
    val document = DocumentImpl("abcdef", true)
    val guard = document.createGuardedBlock(2, 5)

    Assertions.assertThat(document.getRangeGuard(0, 2)).isNull()
    Assertions.assertThat(document.getRangeGuard(5, 6)).isNull()

    guard.isGreedyToLeft = true
    Assertions.assertThat(document.getRangeGuard(0, 2)).isSameAs(guard)
    Assertions.assertThat(document.getRangeGuard(5, 6)).isNull()

    guard.isGreedyToRight = true
    Assertions.assertThat(document.getRangeGuard(5, 6)).isSameAs(guard)
  }

  @Test
  fun `point guards are found at document boundaries`() {
    val document = DocumentImpl("abcdef", true)
    val startGuard = document.createGuardedBlock(0, 0)
    val endGuard = document.createGuardedBlock(document.textLength, document.textLength)

    startGuard.isGreedyToLeft = true
    endGuard.isGreedyToLeft = true

    Assertions.assertThat(document.getOffsetGuard(0)).isSameAs(startGuard)
    Assertions.assertThat(document.getOffsetGuard(document.textLength)).isSameAs(endGuard)
  }

  @Test
  fun `document update excludes an invalid guard`() {
    val document = DocumentImpl("one\ntarget\nlast", true)
    val retainedGuard = document.createGuardedBlock(0, 3)
    val invalidatedGuard = document.createGuardedBlock(4, 10)
    Assertions.assertThat(document.guardedBlocks).containsExactly(retainedGuard, invalidatedGuard)

    document.deleteString(3, 11)

    Assertions.assertThat(retainedGuard.isValid).isTrue()
    Assertions.assertThat(invalidatedGuard.isValid).isFalse()
    Assertions.assertThat(document.guardedBlocks).containsExactly(retainedGuard)
  }

  @Test
  fun `snapshot root strongly retains a guard`() {
    val document = DocumentImpl("abcdef", true)
    val guardReference = createWeakGuard(document, 2, 4)

    GCUtil.tryGcSoftlyReachableObjects()

    Assertions.assertThat(guardReference.get()).isNotNull()
    Assertions.assertThat(document.guardedBlocks).containsExactly(guardReference.get())
  }

  private fun createWeakGuard(document: DocumentImpl, startOffset: Int, endOffset: Int): WeakReference<RangeMarker> {
    return WeakReference(document.createGuardedBlock(startOffset, endOffset))
  }

  private fun textPatch(startOffset: Int, endOffset: Int, newFragment: String): DocumentTextPatch {
    return DocumentTextPatch.simple(
      startOffset = startOffset,
      endOffset = endOffset,
      newFragment = newFragment,
      newModStamp = 1,
      clearLineFlags = false,
    )
  }
}
