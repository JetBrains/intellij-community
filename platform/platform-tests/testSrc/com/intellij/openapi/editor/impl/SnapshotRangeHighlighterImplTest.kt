// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.editor.ex.DocumentTextPatch
import com.intellij.openapi.editor.impl.marker.PMarker
import com.intellij.openapi.editor.impl.marker.PMarkerRoot
import com.intellij.openapi.editor.impl.marker.SnapshotRangeMarkerImpl
import com.intellij.openapi.editor.impl.marker.UsePMarkerImplementation
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test

@TestApplication
@UsePMarkerImplementation
class SnapshotRangeHighlighterImplTest {
  @Test
  @UsePMarkerImplementation(false)
  fun `disabled snapshot marker implementation uses legacy highlighter`() {
    val document = DocumentImpl("abcdef", true)
    val model = DocumentMarkupModel.forDocument(document, null, true) as MarkupModelImpl
    try {
      val highlighter = model.addRangeHighlighter(2, 4, 1, null, HighlighterTargetArea.EXACT_RANGE)
      val persistentHighlighter = model.addPersistentLineHighlighter(null, 0, 1)

      Assertions.assertThat(highlighter).isExactlyInstanceOf(RangeHighlighterImpl::class.java)
      Assertions.assertThat(persistentHighlighter).isExactlyInstanceOf(PersistentRangeHighlighterImpl::class.java)
    }
    finally {
      model.dispose()
    }
  }

  @Test
  fun `persistent highlighters follow line changes in snapshot branches`() {
    val document = DocumentImpl("one\n  target\nlast", true)
    val model = MarkupModelImpl(document)
    try {
      val initialSnapshot = document.core.snapshot()
      val exactHighlighter = model.addRangeHighlighterAndChangeAttributes(
        null, 6, 7, 1, HighlighterTargetArea.EXACT_RANGE, true, null
      )
      val lineHighlighter = model.addPersistentLineHighlighter(null, 1, 1)!!

      Assertions.assertThat(exactHighlighter).isInstanceOf(SnapshotRangeMarkerImpl::class.java)
      Assertions.assertThat(lineHighlighter).isInstanceOf(SnapshotRangeMarkerImpl::class.java)
      Assertions.assertThat(exactHighlighter.isPersistent).isTrue()
      Assertions.assertThat(lineHighlighter.isPersistent).isTrue()
      Assertions.assertThat((exactHighlighter as PMarker).resolve(initialSnapshot))
        .extracting("startOffset", "endOffset").containsExactly(4, 12)
      Assertions.assertThat((lineHighlighter as PMarker).resolve(initialSnapshot))
        .extracting("startOffset", "endOffset").containsExactly(6, 12)

      val insertedBefore = initialSnapshot.applyOp(textPatch(0, 0, "x\n"))
      Assertions.assertThat(exactHighlighter.resolve(insertedBefore))
        .extracting("startOffset", "endOffset").containsExactly(6, 14)
      Assertions.assertThat(lineHighlighter.resolve(insertedBefore))
        .extracting("startOffset", "endOffset").containsExactly(8, 14)

      val replacedText = "prefix\none\n  target\nlast"
      val replaced = initialSnapshot.applyOp(textPatch(0, initialSnapshot.text().length(), replacedText))
      Assertions.assertThat(exactHighlighter.resolve(replaced))
        .extracting("startOffset", "endOffset").containsExactly(11, 19)
      Assertions.assertThat(lineHighlighter.resolve(replaced))
        .extracting("startOffset", "endOffset").containsExactly(13, 19)

      val deleted = initialSnapshot.applyOp(textPatch(4, 13, ""))
      Assertions.assertThat(exactHighlighter.resolve(deleted).isValid).isFalse()
      Assertions.assertThat(lineHighlighter.resolve(deleted).isValid).isFalse()
    }
    finally {
      model.dispose()
    }
  }

  @Test
  fun `highlighter resolves against separate snapshot branches`() {
    val document = DocumentImpl("abcdef", true)
    val model = MarkupModelImpl(document)
    try {
      val initialSnapshot = document.core.snapshot()
      val highlighter = model.addRangeHighlighter(2, 4, 1, null, HighlighterTargetArea.EXACT_RANGE) as PMarker

      val firstBranch = initialSnapshot.applyOp(textPatch(0, 0, "X"))
      val secondBranch = initialSnapshot.applyOp(textPatch(0, 0, "YYYY"))

      Assertions.assertThat(highlighter.resolve(initialSnapshot)).extracting("startOffset", "endOffset").containsExactly(2, 4)
      Assertions.assertThat(highlighter.resolve(firstBranch)).extracting("startOffset", "endOffset").containsExactly(3, 5)
      Assertions.assertThat(highlighter.resolve(secondBranch)).extracting("startOffset", "endOffset").containsExactly(6, 8)
    }
    finally {
      model.dispose()
    }
  }

  @Test
  fun `markup models keep separate highlighter roots`() {
    val document = DocumentImpl("abcdef", true)
    val firstModel = MarkupModelImpl(document)
    val secondModel = MarkupModelImpl(document)
    try {
      val snapshot = document.core.snapshot()
      val firstHighlighter = firstModel.addRangeHighlighter(1, 2, 1, null, HighlighterTargetArea.EXACT_RANGE)
      val secondHighlighter = secondModel.addRangeHighlighter(4, 5, 1, null, HighlighterTargetArea.EXACT_RANGE)
      val first = firstHighlighter as PMarker
      val second = secondHighlighter as PMarker

      Assertions.assertThat(first.resolve(snapshot)).extracting("startOffset", "endOffset").containsExactly(1, 2)
      Assertions.assertThat(second.resolve(snapshot)).extracting("startOffset", "endOffset").containsExactly(4, 5)
      Assertions.assertThat(firstModel.allHighlighters).containsExactly(firstHighlighter)
      Assertions.assertThat(secondModel.allHighlighters).containsExactly(secondHighlighter)
    }
    finally {
      firstModel.dispose()
      secondModel.dispose()
    }
  }

  @Test
  fun `overlapping iterator reads snapshot root lazily`() {
    val document = DocumentImpl("abcd", true)
    val model = MarkupModelImpl(document)
    try {
      val highlighters = (0 until 3).map { index ->
        model.addRangeHighlighter(index, index + 1, 1, null, HighlighterTargetArea.EXACT_RANGE)
      }
      val rootReference = (highlighters.first() as SnapshotRangeMarkerImpl).currentRootReference()
      val root = rootReference.get()
      var readEntryCount = 0
      val countingRoot = object : PMarkerRoot by root {
        override fun overlappingIterator(startOffset: Int, endOffset: Int, tastePreference: Int): Iterator<PMarkerRoot.MarkerEntry> {
          val iterator = root.overlappingIterator(startOffset, endOffset, tastePreference)
          return object : Iterator<PMarkerRoot.MarkerEntry> {
            override fun hasNext(): Boolean = iterator.hasNext()

            override fun next(): PMarkerRoot.MarkerEntry {
              readEntryCount++
              return iterator.next()
            }
          }
        }
      }
      check(rootReference.compareAndSet(root, countingRoot))

      model.overlappingIterator(0, document.textLength).use { iterator ->
        Assertions.assertThat(readEntryCount).isEqualTo(1)
        Assertions.assertThat(iterator.next()).isSameAs(highlighters[0])
        Assertions.assertThat(readEntryCount).isEqualTo(1)
        Assertions.assertThat(iterator.hasNext()).isTrue()
        Assertions.assertThat(readEntryCount).isEqualTo(2)
        Assertions.assertThat(iterator.next()).isSameAs(highlighters[1])
      }
    }
    finally {
      model.dispose()
    }
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
