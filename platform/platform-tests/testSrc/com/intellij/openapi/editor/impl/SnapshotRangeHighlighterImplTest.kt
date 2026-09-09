// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.editor.ex.DocumentTextPatch
import com.intellij.openapi.editor.impl.marker.PMarker
import com.intellij.openapi.editor.impl.marker.PMarkerRoot
import com.intellij.openapi.editor.impl.marker.SnapshotMarkerEngineImpl
import com.intellij.openapi.editor.impl.marker.SnapshotRangeMarkerImpl
import com.intellij.openapi.editor.impl.marker.UsePMarkerImplementation
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.DocumentUtil
import com.intellij.util.ref.GCUtil
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import java.lang.ref.WeakReference

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
      val exactMarker = exactHighlighter as PMarker
      val lineMarker = lineHighlighter as PMarker
      Assertions.assertThat(SnapshotMarkerEngineImpl.resolveRangeMarker(exactMarker, initialSnapshot))
        .extracting("startOffset", "endOffset").containsExactly(4, 12)
      Assertions.assertThat(SnapshotMarkerEngineImpl.resolveRangeMarker(lineMarker, initialSnapshot))
        .extracting("startOffset", "endOffset").containsExactly(6, 12)

      val insertedBefore = initialSnapshot.applyOp(textPatch(0, 0, "x\n"))
      Assertions.assertThat(SnapshotMarkerEngineImpl.resolveRangeMarker(exactMarker, insertedBefore))
        .extracting("startOffset", "endOffset").containsExactly(6, 14)
      Assertions.assertThat(SnapshotMarkerEngineImpl.resolveRangeMarker(lineMarker, insertedBefore))
        .extracting("startOffset", "endOffset").containsExactly(8, 14)

      val replacedText = "prefix\none\n  target\nlast"
      val replaced = initialSnapshot.applyOp(textPatch(0, initialSnapshot.text().length(), replacedText))
      Assertions.assertThat(SnapshotMarkerEngineImpl.resolveRangeMarker(exactMarker, replaced))
        .extracting("startOffset", "endOffset").containsExactly(11, 19)
      Assertions.assertThat(SnapshotMarkerEngineImpl.resolveRangeMarker(lineMarker, replaced))
        .extracting("startOffset", "endOffset").containsExactly(13, 19)

      val deleted = initialSnapshot.applyOp(textPatch(4, 13, ""))
      Assertions.assertThat(SnapshotMarkerEngineImpl.resolveRangeMarker(exactMarker, deleted).isValid).isFalse()
      Assertions.assertThat(SnapshotMarkerEngineImpl.resolveRangeMarker(lineMarker, deleted).isValid).isFalse()
    }
    finally {
      model.dispose()
    }
  }

  @Test
  fun `compound root merges separate highlighter trees`() {
    val document = DocumentImpl("one\ntwo", true)
    val model = MarkupModelImpl(document)
    try {
      val exactHighlighter = model.addRangeHighlighter(
        1, 2, 1, null, HighlighterTargetArea.EXACT_RANGE
      ) as SnapshotRangeMarkerImpl
      val lineHighlighter = model.addRangeHighlighter(
        5, 6, 1, null, HighlighterTargetArea.LINES_IN_RANGE
      ) as SnapshotRangeMarkerImpl
      val rootReference = exactHighlighter.currentRootReference()
      val mergedRoot = CompoundPMarkerRoot.empty().mergeValidMarkersFrom(rootReference.get()) as CompoundPMarkerRoot

      val exactMarkers = mergedRoot.exactRangeRoot.overlappingIterator(0, document.textLength, 0)
        .asSequence()
        .mapNotNull { it.markerReference?.get() }
        .toList()
      val lineMarkers = mergedRoot.linesInRangeRoot.overlappingIterator(0, document.textLength, 0)
        .asSequence()
        .mapNotNull { it.markerReference?.get() }
        .toList()
      Assertions.assertThat(exactMarkers).containsExactly(exactHighlighter)
      Assertions.assertThat(lineMarkers).containsExactly(lineHighlighter)
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

      Assertions.assertThat(SnapshotMarkerEngineImpl.resolveRangeMarker(highlighter, initialSnapshot))
        .extracting("startOffset", "endOffset").containsExactly(2, 4)
      Assertions.assertThat(SnapshotMarkerEngineImpl.resolveRangeMarker(highlighter, firstBranch))
        .extracting("startOffset", "endOffset").containsExactly(3, 5)
      Assertions.assertThat(SnapshotMarkerEngineImpl.resolveRangeMarker(highlighter, secondBranch))
        .extracting("startOffset", "endOffset").containsExactly(6, 8)
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

      Assertions.assertThat(SnapshotMarkerEngineImpl.resolveRangeMarker(first, snapshot))
        .extracting("startOffset", "endOffset").containsExactly(1, 2)
      Assertions.assertThat(SnapshotMarkerEngineImpl.resolveRangeMarker(second, snapshot))
        .extracting("startOffset", "endOffset").containsExactly(4, 5)
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
      val compoundRoot = rootReference.get() as CompoundPMarkerRoot
      val exactRangeRoot = compoundRoot.exactRangeRoot
      var readEntryCount = 0
      val countingRoot = object : PMarkerRoot by exactRangeRoot {
        override fun overlappingIterator(startOffset: Int, endOffset: Int, tastePreference: Int): Iterator<PMarkerRoot.MarkerEntry> {
          val iterator = exactRangeRoot.overlappingIterator(startOffset, endOffset, tastePreference)
          return object : Iterator<PMarkerRoot.MarkerEntry> {
            override fun hasNext(): Boolean = iterator.hasNext()

            override fun next(): PMarkerRoot.MarkerEntry {
              readEntryCount++
              return iterator.next()
            }
          }
        }
      }
      check(rootReference.compareAndSet(compoundRoot, compoundRoot.withExactRangeRoot(countingRoot)))

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

  @Test
  fun `highlighters shift after bulk multi replace at descending offsets`() {
    val document = DocumentImpl("xxxx mmmm yyyy nnnn zzzz tail", true)
    val model = MarkupModelImpl(document)
    try {
      val highlighterA = model.addRangeHighlighter(5, 9, 1, null, HighlighterTargetArea.EXACT_RANGE)
      val highlighterB = model.addRangeHighlighter(15, 19, 1, null, HighlighterTargetArea.EXACT_RANGE)
      val highlighterC = model.addRangeHighlighter(25, 29, 1, null, HighlighterTargetArea.EXACT_RANGE)

      DocumentUtil.executeInBulk(document) {
        document.replaceString(20, 24, "zzzzTT")
        document.replaceString(10, 14, "yyyyTT")
        document.replaceString(0, 4, "xxxxTT")
      }

      Assertions.assertThat(document.text).isEqualTo("xxxxTT mmmm yyyyTT nnnn zzzzTT tail")
      assertValidHighlighter(highlighterA, 7, 11)
      assertValidHighlighter(highlighterB, 19, 23)
      assertValidHighlighter(highlighterC, 31, 35)
    }
    finally {
      model.dispose()
    }
  }

  private fun assertValidHighlighter(highlighter: RangeHighlighter, start: Int, end: Int) {
    Assertions.assertThat(highlighter.isValid).isTrue()
    Assertions.assertThat(highlighter.startOffset).isEqualTo(start)
    Assertions.assertThat(highlighter.endOffset).isEqualTo(end)
  }

  @Test
  fun `snapshot root strongly retains a highlighter`() {
    val document = DocumentImpl("abcdef", true)
    val model = MarkupModelImpl(document)
    try {
      val highlighterReference = createWeakHighlighter(model)

      GCUtil.tryGcSoftlyReachableObjects()

      Assertions.assertThat(highlighterReference.get()).isNotNull()
      Assertions.assertThat(model.allHighlighters).containsExactly(highlighterReference.get())
    }
    finally {
      model.dispose()
    }
  }

  @Test
  fun `disposing storage releases its highlighters`() {
    val document = DocumentImpl("abcdef", true)
    val model = MarkupModelImpl(document)
    val highlighterReference = createWeakHighlighter(model)

    model.dispose()
    GCUtil.tryGcSoftlyReachableObjects { highlighterReference.get() == null }

    Assertions.assertThat(highlighterReference.get()).isNull()
  }

  @Test
  fun `highlighter registry removes collected references`() {
    val document = DocumentImpl("abcdef", true)
    val model = MarkupModelImpl(document)
    try {
      val markerId = createUnrootedHighlighter(model)
      Assertions.assertThat(model.containsSnapshotHighlighterId(markerId)).isTrue()

      GCUtil.tryGcSoftlyReachableObjects {
        !model.containsSnapshotHighlighterId(markerId)
      }

      Assertions.assertThat(model.containsSnapshotHighlighterId(markerId)).isFalse()
    }
    finally {
      model.dispose()
    }
  }

  private fun createWeakHighlighter(model: MarkupModelImpl): WeakReference<RangeHighlighter> {
    val highlighter = model.addRangeHighlighter(2, 4, 1, null, HighlighterTargetArea.EXACT_RANGE)
    return WeakReference(highlighter)
  }

  private fun createUnrootedHighlighter(model: MarkupModelImpl): Long {
    val highlighter = model.addRangeHighlighter(2, 4, 1, null, HighlighterTargetArea.EXACT_RANGE) as SnapshotRangeMarkerImpl
    val rootReference = highlighter.currentRootReference()
    val root = rootReference.get()
    val entry = root.overlappingIterator(0, model.document.textLength, 0).next()
    check(entry.markerReference?.get() === highlighter)
    rootReference.set(root.remove(entry.markerId))
    return entry.markerId
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
