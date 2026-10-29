// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.ex.DocumentPatch
import com.intellij.openapi.editor.ex.DocumentText
import com.intellij.openapi.editor.ex.PrioritizedDocumentListener
import com.intellij.openapi.editor.ex.RangeHighlighterEx
import com.intellij.openapi.editor.impl.event.MarkupModelListener
import com.intellij.openapi.editor.impl.marker.MarkerPolicy
import com.intellij.openapi.editor.impl.marker.MarkerSpec
import com.intellij.openapi.editor.impl.marker.PMarkerResolution
import com.intellij.openapi.editor.impl.marker.PMarkerRoot
import com.intellij.openapi.editor.impl.marker.SnapshotMarkerEngineImpl
import com.intellij.openapi.editor.impl.marker.SnapshotMarkerReference
import com.intellij.openapi.editor.impl.marker.SnapshotRangeMarkerImpl
import com.intellij.openapi.editor.impl.marker.UsePMarkerImplementation
import com.intellij.openapi.editor.markup.FontFamilies
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.TextRangeScalarUtil
import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.util.DocumentUtil
import com.intellij.util.ref.GCUtil
import com.intellij.util.ref.GCWatcher
import com.intellij.util.ui.EmptyIcon
import it.unimi.dsi.fastutil.longs.LongArrayList
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.awt.Color
import java.awt.Font
import java.lang.ref.WeakReference
import java.util.concurrent.CountDownLatch
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Consumer
import javax.swing.Icon

@Suppress("SuspiciousPackagePrivateAccess")
@TestApplication
@UsePMarkerImplementation
class SnapshotRangeHighlighterImplTest {
  @Test
  fun `compound batches preserve both highlighter trees`() {
    val document = DocumentImpl("one\ntwo", true)
    val model = MarkupModelImpl(document)
    try {
      val exact = model.addRangeHighlighter(1, 2, 1, null, HighlighterTargetArea.EXACT_RANGE) as SnapshotRangeMarkerImpl
      val line = model.addRangeHighlighter(5, 6, 1, null, HighlighterTargetArea.LINES_IN_RANGE) as SnapshotRangeMarkerImpl
      val original = exact.currentRootReference().get() as CompoundPMarkerRoot
      val entries = original.overlappingIterator(0, document.textLength, 0).asSequence().toList()
      val root = original.emptyRoot().insertMany(entries) as CompoundPMarkerRoot
      val ids = listOf(line.markerHandle, exact.markerHandle, line.markerHandle, root.createStrongHandle(exact))

      Assertions.assertThat(root.exactRangeRoot.resolve(exact.markerHandle)).isEqualTo(
        original.exactRangeRoot.resolve(exact.markerHandle)
      )
      Assertions.assertThat(root.linesInRangeRoot.resolve(line.markerHandle)).isEqualTo(
        original.linesInRangeRoot.resolve(line.markerHandle)
      )
      val removed = root.removeMany(ids)
      val purged = root.purgeMany(ids)
      Assertions.assertThat(removed.isEmpty()).isTrue()
      Assertions.assertThat(purged.isEmpty()).isTrue()
      for (entry in entries) {
        Assertions.assertThat(removed.resolve(entry.handle)).isEqualTo(
          root.remove(entry.handle).resolve(entry.handle)
        )
        Assertions.assertThat(purged.resolve(entry.handle)).isEqualTo(PMarkerResolution.ABSENT)
      }
      Assertions.assertThat(root.isEmpty()).isFalse()
      Assertions.assertThat(root.insertMany(emptyList())).isSameAs(root)
      Assertions.assertThat(root.removeMany(emptyList())).isSameAs(root)
      Assertions.assertThat(root.purgeMany(emptyList())).isSameAs(root)
    }
    finally {
      model.dispose()
    }
  }

  @Test
  fun `compound root is empty only when both trees have no valid markers`() {
    val document = DocumentImpl("one\ntwo", true)
    val model = MarkupModelImpl(document)
    try {
      Assertions.assertThat(CompoundPMarkerRoot.empty().isEmpty()).isTrue()
      val exact = model.addRangeHighlighter(1, 2, 1, null, HighlighterTargetArea.EXACT_RANGE) as SnapshotRangeMarkerImpl
      val line = model.addRangeHighlighter(5, 6, 1, null, HighlighterTargetArea.LINES_IN_RANGE) as SnapshotRangeMarkerImpl
      val root = exact.currentRootReference().get()

      Assertions.assertThat(root.isValid(exact.markerHandle)).isTrue()
      Assertions.assertThat(root.isValid(line.markerHandle)).isTrue()
      Assertions.assertThat(root.isValid(root.createStrongHandle(exact))).isFalse()
      Assertions.assertThat(root.remove(exact.markerHandle).isValid(exact.markerHandle)).isFalse()
      Assertions.assertThat(root.remove(line.markerHandle).isValid(line.markerHandle)).isFalse()
      Assertions.assertThat(root.isEmpty()).isFalse()
      Assertions.assertThat(root.remove(exact.markerHandle).isEmpty()).isFalse()
      Assertions.assertThat(root.remove(line.markerHandle).isEmpty()).isFalse()
      Assertions.assertThat(root.remove(exact.markerHandle).remove(line.markerHandle).isEmpty()).isTrue()
    }
    finally {
      model.dispose()
    }
  }

  @Test
  fun `a font families change is a font style change`(@TestDisposable disposable: Disposable) {
    val document = DocumentImpl("abc", true)
    val model = MarkupModelImpl(document)
    try {
      val highlighter = model.addRangeHighlighter(0, 3, 0, null, HighlighterTargetArea.EXACT_RANGE) as RangeHighlighterEx
      Assertions.assertThat(highlighter.javaClass.simpleName).isEqualTo("SnapshotRangeHighlighterImpl")
      val fontStyleChanges = ArrayList<Boolean>()
      model.addMarkupModelListener(disposable, object : MarkupModelListener {
        override fun attributesChanged(
          highlighter: RangeHighlighterEx,
          renderersChanged: Boolean,
          fontStyleChanged: Boolean,
          foregroundColorChanged: Boolean,
        ) {
          fontStyleChanges.add(fontStyleChanged)
        }
      })

      highlighter.setTextAttributes(attributes(Color.RED, FontFamilies.of("JetBrains Mono")))
      highlighter.setTextAttributes(attributes(Color.RED, FontFamilies.of("Noto Serif")))
      highlighter.setTextAttributes(attributes(Color.BLUE, FontFamilies.of("Noto Serif")))

      Assertions.assertThat(fontStyleChanges).containsExactly(true, true, false)
    }
    finally {
      model.dispose()
    }
  }

  private fun attributes(background: Color, fontFamilies: FontFamilies): TextAttributes {
    val attributes = TextAttributes(null, background, null, null, Font.PLAIN)
    attributes.fontFamilies = fontFamilies
    return attributes
  }

  @Test
  fun `after removed listener can remove another highlighter`(@TestDisposable disposable: Disposable) {
    val document = DocumentImpl("abcdef", true)
    val model = MarkupModelImpl(document)
    try {
      val first = model.addRangeHighlighter(1, 2, 1, null, HighlighterTargetArea.EXACT_RANGE)
      val second = model.addRangeHighlighter(3, 4, 1, null, HighlighterTargetArea.EXACT_RANGE)
      val removed = ArrayList<RangeHighlighter>()
      model.addMarkupModelListener(disposable, object : MarkupModelListener {
        override fun afterRemoved(highlighter: RangeHighlighterEx) {
          removed.add(highlighter)
          if (highlighter === first) {
            model.removeHighlighter(second)
          }
        }
      })

      first.dispose()

      Assertions.assertThat(removed).containsExactly(first, second)
      Assertions.assertThat(model.allHighlighters).isEmpty()
    }
    finally {
      model.dispose()
    }
  }

  @Test
  fun `disposal preserves callback order and offsets without holding the monitor`(@TestDisposable disposable: Disposable) {
    val document = DocumentImpl("abcdef", true)
    val model = MarkupModelImpl(document)
    try {
      val first = model.addRangeHighlighter(1, 2, 1, null, HighlighterTargetArea.EXACT_RANGE) as RangeHighlighterEx
      val second = model.addRangeHighlighter(3, 4, 1, null, HighlighterTargetArea.EXACT_RANGE) as RangeHighlighterEx
      val events = ArrayList<String>()
      model.addMarkupModelListener(disposable, object : MarkupModelListener {
        override fun beforeRemoved(highlighter: RangeHighlighterEx) {
          Assertions.assertThat(Thread.holdsLock(highlighter)).isFalse()
          Assertions.assertThat(highlighter.isValid).isTrue()
          events.add("before ${highlighter.id}")
        }

        override fun afterRemoved(highlighter: RangeHighlighterEx) {
          Assertions.assertThat(Thread.holdsLock(highlighter)).isFalse()
          Assertions.assertThat(highlighter.isValid).isFalse()
          Assertions.assertThat(model.allHighlighters).doesNotContain(highlighter)
          val root = checkNotNull(model.rootStore().root(document.core.snapshot()))
          Assertions.assertThat(PMarkerResolution.isAbsent(root.resolve((highlighter as SnapshotRangeMarkerImpl).markerHandle))).isTrue()
          model.overlappingIterator(0, document.textLength).use { iterator ->
            Assertions.assertThat(iterator.asSequence().toList()).doesNotContain(highlighter)
          }
          events.add("after ${highlighter.id}")
        }
      })

      first.dispose()
      second.dispose()

      Assertions.assertThat(events).containsExactly("before ${first.id}", "after ${first.id}", "before ${second.id}", "after ${second.id}")
      Assertions.assertThat(first.textRange).isEqualTo(TextRange(1, 2))
      Assertions.assertThat(second.textRange).isEqualTo(TextRange(3, 4))
    }
    finally {
      model.dispose()
    }
  }

  @Test
  fun `disposal retains offsets during renderer disposal without holding the monitor`() {
    val document = DocumentImpl("abcdef", true)
    val model = MarkupModelImpl(document)
    try {
      val highlighter = model.addRangeHighlighter(1, 2, 1, null, HighlighterTargetArea.EXACT_RANGE) as RangeHighlighterEx
      var disposedRenderer = false
      highlighter.gutterIconRenderer = object : GutterIconRenderer(), Disposable {
        override fun getIcon(): Icon = EmptyIcon.ICON_16

        override fun equals(other: Any?): Boolean = this === other

        override fun hashCode(): Int = System.identityHashCode(this)

        override fun dispose() {
          Assertions.assertThat(Thread.holdsLock(highlighter)).isFalse()
          document.insertString(0, "x")
          Assertions.assertThat(highlighter.textRange).isEqualTo(TextRange(1, 2))
          disposedRenderer = true
        }
      }

      highlighter.dispose()

      Assertions.assertThat(disposedRenderer).isTrue()
      Assertions.assertThat(model.allHighlighters).isEmpty()
      val root = checkNotNull(model.rootStore().root(document.core.snapshot()))
      val resolution = root.resolve((highlighter as SnapshotRangeMarkerImpl).markerHandle)
      Assertions.assertThat(PMarkerResolution.isAbsent(resolution)).isTrue()
      Assertions.assertThat(resolution).isEqualTo(PMarkerResolution.ABSENT)
    }
    finally {
      model.dispose()
    }
  }

  @Test
  fun `concurrent and nested disposal do not wait for removal listeners`(@TestDisposable disposable: Disposable) {
    val document = DocumentImpl("abcdef", true)
    val model = MarkupModelImpl(document)
    try {
      val highlighter = model.addRangeHighlighter(1, 2, 1, null, HighlighterTargetArea.EXACT_RANGE) as RangeHighlighterEx
      val entered = CountDownLatch(1)
      val resume = CountDownLatch(1)
      val beforeCalls = AtomicInteger()
      val afterCalls = AtomicInteger()
      model.addMarkupModelListener(disposable, object : MarkupModelListener {
        override fun beforeRemoved(highlighter: RangeHighlighterEx) {
          Assertions.assertThat(Thread.holdsLock(highlighter)).isFalse()
          beforeCalls.incrementAndGet()
          highlighter.dispose()
          entered.countDown()
          check(resume.await(10, TimeUnit.SECONDS)) { "The removal listener did not resume" }
        }

        override fun afterRemoved(highlighter: RangeHighlighterEx) {
          Assertions.assertThat(Thread.holdsLock(highlighter)).isFalse()
          afterCalls.incrementAndGet()
          highlighter.dispose()
        }
      })
      val removal = FutureTask { highlighter.dispose() }
      val competingRemoval = FutureTask { highlighter.dispose() }
      val removalThread = Thread(removal, "Highlighter removal")
      val competingThread = Thread(competingRemoval, "Concurrent highlighter removal")
      removalThread.start()
      try {
        check(entered.await(10, TimeUnit.SECONDS)) { "The removal listener did not start" }
        competingThread.start()
        competingRemoval.get(10, TimeUnit.SECONDS)
        Assertions.assertThat(beforeCalls.get()).isEqualTo(1)
        Assertions.assertThat(afterCalls.get()).isZero()
        Assertions.assertThat(highlighter.isValid).isTrue()
      }
      finally {
        resume.countDown()
        removalThread.join(TimeUnit.SECONDS.toMillis(10))
        competingThread.join(TimeUnit.SECONDS.toMillis(10))
        check(!removalThread.isAlive && !competingThread.isAlive) { "The removal threads did not stop" }
      }
      removal.get(10, TimeUnit.SECONDS)

      Assertions.assertThat(beforeCalls.get()).isEqualTo(1)
      Assertions.assertThat(afterCalls.get()).isEqualTo(1)
      Assertions.assertThat(highlighter.isValid).isFalse()
      Assertions.assertThat(model.allHighlighters).isEmpty()
      val root = checkNotNull(model.rootStore().root(document.core.snapshot()))
      Assertions.assertThat(PMarkerResolution.isAbsent(root.resolve((highlighter as SnapshotRangeMarkerImpl).markerHandle))).isTrue()
    }
    finally {
      model.dispose()
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = [true, false])
  fun `disposal retries only when a before listener fails`(beforeRemoval: Boolean, @TestDisposable disposable: Disposable) {
    val document = DocumentImpl("abcdef", true)
    val model = MarkupModelImpl(document)
    try {
      val highlighter = model.addRangeHighlighter(1, 2, 1, null, HighlighterTargetArea.EXACT_RANGE) as RangeHighlighterEx
      val failure = IllegalStateException("The removal callback failed")
      var failCallbacks = true
      var beforeCalls = 0
      var afterCalls = 0
      model.addMarkupModelListener(disposable, object : MarkupModelListener {
        override fun beforeRemoved(highlighter: RangeHighlighterEx) {
          beforeCalls++
          if (failCallbacks && beforeRemoval) throw failure
        }

        override fun afterRemoved(highlighter: RangeHighlighterEx) {
          afterCalls++
          if (failCallbacks) throw failure
        }
      })

      Assertions.assertThatThrownBy { highlighter.dispose() }.isSameAs(failure)

      val root = checkNotNull(model.rootStore().root(document.core.snapshot()))
      Assertions.assertThat(PMarkerResolution.isAbsent(root.resolve((highlighter as SnapshotRangeMarkerImpl).markerHandle)))
        .isEqualTo(!beforeRemoval)
      Assertions.assertThat(highlighter.isValid).isEqualTo(beforeRemoval)
      Assertions.assertThat(beforeCalls).isEqualTo(1)
      Assertions.assertThat(afterCalls).isEqualTo(if (beforeRemoval) 0 else 1)
      if (beforeRemoval) Assertions.assertThat(model.allHighlighters).containsExactly(highlighter)
      else Assertions.assertThat(model.allHighlighters).isEmpty()

      failCallbacks = false
      highlighter.dispose()
      highlighter.dispose()

      Assertions.assertThat(beforeCalls).isEqualTo(if (beforeRemoval) 2 else 1)
      Assertions.assertThat(afterCalls).isEqualTo(1)
      Assertions.assertThat(highlighter.isValid).isFalse()
      Assertions.assertThat(model.allHighlighters).isEmpty()
      val removedRoot = checkNotNull(model.rootStore().root(document.core.snapshot()))
      Assertions.assertThat(PMarkerResolution.isAbsent(removedRoot.resolve((highlighter as SnapshotRangeMarkerImpl).markerHandle))).isTrue()
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
      val markerStores = document.snapshotMarkerStores

      Assertions.assertThat(exactHighlighter).isInstanceOf(SnapshotRangeMarkerImpl::class.java)
      Assertions.assertThat(lineHighlighter).isInstanceOf(SnapshotRangeMarkerImpl::class.java)
      Assertions.assertThat(exactHighlighter.isPersistent).isTrue()
      Assertions.assertThat(lineHighlighter.isPersistent).isTrue()
      val exactMarker = exactHighlighter as SnapshotRangeMarkerImpl
      val lineMarker = lineHighlighter as SnapshotRangeMarkerImpl
      val rootStore = model.rootStore()
      Assertions.assertThat(SnapshotMarkerEngineImpl.resolveRangeMarker(exactMarker, rootStore.rootReference(initialSnapshot).get()))
        .isEqualTo(PMarkerResolution.valid(TextRangeScalarUtil.toScalarRange(4, 12)))
      Assertions.assertThat(SnapshotMarkerEngineImpl.resolveRangeMarker(lineMarker, rootStore.rootReference(initialSnapshot).get()))
        .isEqualTo(PMarkerResolution.valid(TextRangeScalarUtil.toScalarRange(6, 12)))

      val insertedBefore = markerStores.applyPatch(initialSnapshot, textPatch(0, 0, "x\n"))
      Assertions.assertThat(SnapshotMarkerEngineImpl.resolveRangeMarker(exactMarker, rootStore.rootReference(insertedBefore).get()))
        .isEqualTo(PMarkerResolution.valid(TextRangeScalarUtil.toScalarRange(6, 14)))
      Assertions.assertThat(SnapshotMarkerEngineImpl.resolveRangeMarker(lineMarker, rootStore.rootReference(insertedBefore).get()))
        .isEqualTo(PMarkerResolution.valid(TextRangeScalarUtil.toScalarRange(8, 14)))

      val replacedText = "prefix\none\n  target\nlast"
      val replaced = markerStores.applyPatch(initialSnapshot, textPatch(0, initialSnapshot.text().length(), replacedText))
      Assertions.assertThat(SnapshotMarkerEngineImpl.resolveRangeMarker(exactMarker, rootStore.rootReference(replaced).get()))
        .isEqualTo(PMarkerResolution.valid(TextRangeScalarUtil.toScalarRange(11, 19)))
      Assertions.assertThat(SnapshotMarkerEngineImpl.resolveRangeMarker(lineMarker, rootStore.rootReference(replaced).get()))
        .isEqualTo(PMarkerResolution.valid(TextRangeScalarUtil.toScalarRange(13, 19)))

      val deleted = markerStores.applyPatch(initialSnapshot, textPatch(4, 13, ""))
      val deletedExactResolution = SnapshotMarkerEngineImpl.resolveRangeMarker(exactMarker, rootStore.rootReference(deleted).get())
      val deletedLineResolution = SnapshotMarkerEngineImpl.resolveRangeMarker(lineMarker, rootStore.rootReference(deleted).get())
      Assertions.assertThat(PMarkerResolution.isValid(deletedExactResolution)).isFalse()
      Assertions.assertThat(PMarkerResolution.isValid(deletedLineResolution)).isFalse()
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
      val mergedRoot = rootReference.get().emptyRoot().mergeValidMarkersFrom(rootReference.get()) as CompoundPMarkerRoot

      val exactMarkers = mergedRoot.exactRangeRoot.overlappingIterator(0, document.textLength, 0)
        .asSequence()
        .mapNotNull { it.marker }
        .toList()
      val lineMarkers = mergedRoot.linesInRangeRoot.overlappingIterator(0, document.textLength, 0)
        .asSequence()
        .mapNotNull { it.marker }
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
      val highlighter = model.addRangeHighlighter(2, 4, 1, null, HighlighterTargetArea.EXACT_RANGE) as SnapshotRangeMarkerImpl
      val markerStores = document.snapshotMarkerStores
      val rootStore = model.rootStore()

      val firstBranch = markerStores.applyPatch(initialSnapshot, textPatch(0, 0, "X"))
      val secondBranch = markerStores.applyPatch(initialSnapshot, textPatch(0, 0, "YYYY"))

      Assertions.assertThat(SnapshotMarkerEngineImpl.resolveRangeMarker(highlighter, rootStore.rootReference(initialSnapshot).get()))
        .isEqualTo(PMarkerResolution.valid(TextRangeScalarUtil.toScalarRange(2, 4)))
      Assertions.assertThat(SnapshotMarkerEngineImpl.resolveRangeMarker(highlighter, rootStore.rootReference(firstBranch).get()))
        .isEqualTo(PMarkerResolution.valid(TextRangeScalarUtil.toScalarRange(3, 5)))
      Assertions.assertThat(SnapshotMarkerEngineImpl.resolveRangeMarker(highlighter, rootStore.rootReference(secondBranch).get()))
        .isEqualTo(PMarkerResolution.valid(TextRangeScalarUtil.toScalarRange(6, 8)))
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
      val first = firstHighlighter as SnapshotRangeMarkerImpl
      val second = secondHighlighter as SnapshotRangeMarkerImpl
      val firstRootStore = firstModel.rootStore()
      val secondRootStore = secondModel.rootStore()

      Assertions.assertThat(SnapshotMarkerEngineImpl.resolveRangeMarker(first, firstRootStore.rootReference(snapshot).get()))
        .isEqualTo(PMarkerResolution.valid(TextRangeScalarUtil.toScalarRange(1, 2)))
      Assertions.assertThat(SnapshotMarkerEngineImpl.resolveRangeMarker(second, secondRootStore.rootReference(snapshot).get()))
        .isEqualTo(PMarkerResolution.valid(TextRangeScalarUtil.toScalarRange(4, 5)))
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
  fun `snapshot publication retains concurrently added highlighters`() {
    val document = DocumentImpl("abcdef", true)
    val model = MarkupModelImpl(document)
    try {
      val first = model.addRangeHighlighter(0, 1, 1, null, HighlighterTargetArea.EXACT_RANGE) as RangeHighlighterEx
      val reference = changeHighlighterDuringSnapshotChange(model) { createWeakHighlighter(model) }

      GCUtil.tryGcSoftlyReachableObjects()

      val added = checkNotNull(reference.get()) as RangeHighlighterEx
      assertValidHighlighter(added, 2, 4)
      Assertions.assertThat(model.allHighlighters).containsExactly(first, added)
      model.overlappingIterator(0, document.textLength).use { iterator ->
        Assertions.assertThat(iterator.asSequence().toList()).containsExactly(first, added)
      }
    }
    finally {
      model.dispose()
    }
  }

  @Test
  fun `snapshot publication preserves concurrent highlighter specifications`() {
    val document = DocumentImpl("abcdef", true)
    val model = MarkupModelImpl(document)
    try {
      val highlighter = model.addRangeHighlighter(2, 4, 1, null, HighlighterTargetArea.EXACT_RANGE)

      changeHighlighterDuringSnapshotChange(model) { highlighter.isGreedyToRight = true }

      val root = checkNotNull(model.rootStore().root(document.core.snapshot()))
      val entry = root.overlappingIterator(0, document.textLength, 0).next()
      Assertions.assertThat(MarkerSpec.isGreedyToRight(entry.spec)).isTrue()
    }
    finally {
      model.dispose()
    }
  }

  @Test
  fun `snapshot publication preserves concurrent highlighter flavors`() {
    val document = DocumentImpl("abcdef", true)
    val model = MarkupModelImpl(document)
    try {
      val highlighter = model.addRangeHighlighter(2, 4, 1, null, HighlighterTargetArea.EXACT_RANGE)

      changeHighlighterDuringSnapshotChange(model) { highlighter.setErrorStripeMarkColor(Color.RED) }

      val root = checkNotNull(model.rootStore().root(document.core.snapshot()))
      val entries = root.overlappingIterator(0, document.textLength, RangeHighlighterTree.ERROR_STRIPE_FLAVOR_FLAG.toInt())
      Assertions.assertThat(entries.asSequence().map { it.marker?.id }.toList())
        .containsExactly((highlighter as RangeHighlighterEx).id)
    }
    finally {
      model.dispose()
    }
  }

  @Test
  fun `snapshot publication preserves concurrent highlighter removal`() {
    val document = DocumentImpl("abcdef", true)
    val model = MarkupModelImpl(document)
    try {
      val highlighter = model.addRangeHighlighter(2, 4, 1, null, HighlighterTargetArea.EXACT_RANGE)

      changeHighlighterDuringSnapshotChange(model) { model.removeHighlighter(highlighter) }

      Assertions.assertThat(model.allHighlighters).isEmpty()
      model.overlappingIterator(0, document.textLength).use { iterator ->
        Assertions.assertThat(iterator.hasNext()).isFalse()
      }
    }
    finally {
      model.dispose()
    }
  }

  @Test
  fun `snapshot publication includes the first highlighter in an existing store`() {
    val document = DocumentImpl("abcdef", true)
    val model = MarkupModelImpl(document)
    val emptyModel = MarkupModelImpl(document)
    try {
      model.addRangeHighlighter(0, 1, 1, null, HighlighterTargetArea.EXACT_RANGE)
      Assertions.assertThat(emptyModel.rootStore().containsSnapshot(document.core.snapshot())).isFalse()

      val highlighter = changeHighlighterDuringSnapshotChange(model, insertOffset = 0) {
        emptyModel.addRangeHighlighter(2, 4, 1, null, HighlighterTargetArea.EXACT_RANGE)
      }

      assertValidHighlighter(highlighter, 3, 5)
      Assertions.assertThat(emptyModel.allHighlighters).containsExactly(highlighter)
    }
    finally {
      model.dispose()
      emptyModel.dispose()
    }
  }

  @Test
  fun `snapshot publication includes a concurrently registered store`() {
    val document = DocumentImpl("abcdef", true)
    val model = MarkupModelImpl(document)
    var addedModel: MarkupModelImpl? = null
    try {
      model.addRangeHighlighter(0, 1, 1, null, HighlighterTargetArea.EXACT_RANGE)

      val highlighter = changeHighlighterDuringSnapshotChange(model, insertOffset = 0) {
        val newModel = MarkupModelImpl(document)
        addedModel = newModel
        newModel.addRangeHighlighter(2, 4, 1, null, HighlighterTargetArea.EXACT_RANGE)
      }

      assertValidHighlighter(highlighter, 3, 5)
      Assertions.assertThat(checkNotNull(addedModel).allHighlighters).containsExactly(highlighter)
    }
    finally {
      model.dispose()
      addedModel?.dispose()
    }
  }

  @Test
  fun `snapshot publication does not restore a concurrently disposed store`() {
    val document = DocumentImpl("abcdef", true)
    val model = MarkupModelImpl(document)
    val removedModel = MarkupModelImpl(document)
    val removedStore = removedModel.rootStore()
    try {
      model.addRangeHighlighter(0, 1, 1, null, HighlighterTargetArea.EXACT_RANGE)
      removedModel.addRangeHighlighter(2, 4, 1, null, HighlighterTargetArea.EXACT_RANGE)

      changeHighlighterDuringSnapshotChange(model) { removedModel.dispose() }

      Assertions.assertThat(removedStore.containsSnapshot(document.core.snapshot())).isFalse()
    }
    finally {
      model.dispose()
      removedModel.dispose()
    }
  }

  @Test
  fun `highlighter specification retries after concurrent snapshot publication`() {
    val document = DocumentImpl("abcdef", true)
    val model = MarkupModelImpl(document)
    try {
      val highlighter = model.addRangeHighlighter(2, 4, 1, null, HighlighterTargetArea.EXACT_RANGE)
      val marker = highlighter as SnapshotRangeMarkerImpl
      val reference = marker.currentRootReference()
      val root = reference.get() as CompoundPMarkerRoot
      val transformed = CountDownLatch(1)
      val commit = CountDownLatch(1)
      val pausingRoot = object : PMarkerRoot by root.exactRangeRoot {
        override fun updateSpec(handle: SnapshotMarkerReference<*>, spec: Byte): PMarkerRoot {
          Assertions.assertThat(Thread.holdsLock(document.snapshotMarkerStores)).isFalse()
          val result = root.exactRangeRoot.updateSpec(handle, spec)
          transformed.countDown()
          check(commit.await(10, TimeUnit.SECONDS)) { "The highlighter change did not resume" }
          return result
        }
      }
      reference.set(root.withExactRangeRoot(pausingRoot))
      val modification = FutureTask { highlighter.isGreedyToRight = true }
      val modificationThread = Thread(modification, "Concurrent highlighter specification change")
      modificationThread.start()
      try {
        check(transformed.await(10, TimeUnit.SECONDS)) { "The highlighter specification was not transformed" }
        document.insertString(0, "x")
        commit.countDown()
        modification.get(10, TimeUnit.SECONDS)
      }
      finally {
        commit.countDown()
        modificationThread.join(TimeUnit.SECONDS.toMillis(10))
        check(!modificationThread.isAlive) { "The highlighter specification thread did not stop" }
      }

      assertValidHighlighter(highlighter, 3, 5)
      val entry = marker.currentRootReference().get().overlappingIterator(0, document.textLength, 0).next()
      Assertions.assertThat(MarkerSpec.isGreedyToRight(entry.spec)).isTrue()
      Assertions.assertThat(highlighter.isGreedyToRight).isTrue()
    }
    finally {
      model.dispose()
    }
  }

  private fun <T> changeHighlighterDuringSnapshotChange(
    model: MarkupModelImpl,
    insertOffset: Int = model.document.textLength,
    change: () -> T,
  ): T {
    val document = model.document as DocumentImpl
    val rootReference = model.rootStore().rootReference(document.core.snapshot())
    val root = rootReference.get() as CompoundPMarkerRoot
    val transformed = CountDownLatch(1)
    val publish = CountDownLatch(1)
    val pausingRoot = object : PMarkerRoot by root.exactRangeRoot {
      override fun applyPatch(
        patch: DocumentPatch,
        beforeText: DocumentText,
        afterText: DocumentText,
        invalidatedMarkerConsumer: Consumer<in SnapshotMarkerReference<*>>,
        affectedMarkerConsumer: Consumer<in SnapshotMarkerReference<*>>,
      ): PMarkerRoot {
        Assertions.assertThat(Thread.holdsLock(document.snapshotMarkerStores)).isFalse()
        val result = root.exactRangeRoot.applyPatch(patch, beforeText, afterText, invalidatedMarkerConsumer, affectedMarkerConsumer)
        transformed.countDown()
        check(publish.await(10, TimeUnit.SECONDS)) { "The snapshot publication did not resume" }
        return result
      }
    }
    rootReference.set(root.withExactRangeRoot(pausingRoot))
    val transition = FutureTask { document.insertString(insertOffset, "x") }
    val modification = FutureTask { change() }
    val transitionThread = Thread(transition, "Highlighter snapshot transition")
    val modificationThread = Thread(modification, "Concurrent highlighter change")
    transitionThread.start()
    try {
      check(transformed.await(10, TimeUnit.SECONDS)) { "The snapshot root was not transformed" }
      modificationThread.start()
      val result = modification.get(10, TimeUnit.SECONDS)
      publish.countDown()
      transition.get(10, TimeUnit.SECONDS)
      return result
    }
    finally {
      publish.countDown()
      transitionThread.join(TimeUnit.SECONDS.toMillis(10))
      modificationThread.join(TimeUnit.SECONDS.toMillis(10))
      check(!transitionThread.isAlive && !modificationThread.isAlive) { "The concurrent highlighter threads did not stop" }
    }
  }

  @Test
  fun `markup model strongly retains a highlighter`() {
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
  fun `removing a highlighter releases it`() {
    val document = DocumentImpl("abcdef", true)
    val model = MarkupModelImpl(document)
    try {
      val watcher = createAndReleaseHighlighter(model) { model.removeHighlighter(it) }

      watcher.ensureCollectedWithinTimeout(10_000)
    }
    finally {
      model.dispose()
    }
  }

  @Test
  fun `invalidating a highlighter releases it`() {
    val document = DocumentImpl("abcdef", true)
    val model = MarkupModelImpl(document)
    try {
      val watcher = createAndReleaseHighlighter(model) { document.deleteString(0, document.textLength) }

      watcher.ensureCollectedWithinTimeout(10_000) {
        model.rootStore().processQueue()
      }
    }
    finally {
      model.dispose()
    }
  }

  @Test
  fun `failed insertion removes the highlighter from the registry`() {
    val document = DocumentImpl("abcdef", true)
    val model = MarkupModelImpl(document)
    try {
      val first = model.addRangeHighlighter(0, 1, 1, null, HighlighterTargetArea.EXACT_RANGE)
      val reference = (first as SnapshotRangeMarkerImpl).currentRootReference()
      val root = reference.get() as CompoundPMarkerRoot
      val insertedIds = LongArrayList()
      val failure = IllegalStateException("The highlighter insertion failed")
      val failingRoot = object : PMarkerRoot by root.exactRangeRoot {
        override fun insert(
          handle: SnapshotMarkerReference<*>,
          startOffset: Int,
          endOffset: Int,
          spec: Byte,
          flavorFlags: Byte,
          measure: Int,
          policy: MarkerPolicy,
        ): PMarkerRoot {
          insertedIds.add(checkNotNull(handle.get()).id)
          throw failure
        }
      }
      reference.set(root.withExactRangeRoot(failingRoot))

      Assertions.assertThatThrownBy {
        model.addRangeHighlighter(2, 4, 1, null, HighlighterTargetArea.EXACT_RANGE)
      }.isSameAs(failure)

      Assertions.assertThat(insertedIds.size).isEqualTo(1)
      Assertions.assertThat(model.containsSnapshotHighlighterId(insertedIds.getLong(0))).isFalse()
      Assertions.assertThat(model.allHighlighters).containsExactly(first)
    }
    finally {
      model.dispose()
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = [true, false])
  fun `invalidation clears the registry when a removal listener fails`(beforeRemoval: Boolean, @TestDisposable disposable: Disposable) {
    val document = DocumentImpl("abcdef", true)
    val model = MarkupModelImpl(document)
    try {
      val first = model.addRangeHighlighter(0, 1, 1, null, HighlighterTargetArea.EXACT_RANGE) as RangeHighlighterEx
      val second = model.addRangeHighlighter(2, 4, 1, null, HighlighterTargetArea.EXACT_RANGE) as RangeHighlighterEx
      Assertions.assertThat(first).isInstanceOf(SnapshotRangeMarkerImpl::class.java)
      Assertions.assertThat(second).isInstanceOf(SnapshotRangeMarkerImpl::class.java)
      val failure = IllegalStateException("The highlighter removal listener failed")
      model.addMarkupModelListener(disposable, object : MarkupModelListener {
        override fun beforeRemoved(highlighter: RangeHighlighterEx) {
          if (beforeRemoval) throw failure
        }

        override fun afterRemoved(highlighter: RangeHighlighterEx) {
          if (!beforeRemoval) throw failure
        }
      })
      Assertions.assertThat(model.allHighlighters).containsExactly(first, second)

      val logged = LoggedErrorProcessor.executeAndReturnLoggedError { document.deleteString(0, document.textLength) }

      Assertions.assertThat(logged).isSameAs(failure)
      Assertions.assertThat(model.containsSnapshotHighlighterId(first.id)).isFalse()
      Assertions.assertThat(model.containsSnapshotHighlighterId(second.id)).isFalse()
      Assertions.assertThat(model.allHighlighters).isEmpty()
      val added = model.addRangeHighlighter(0, 0, 1, null, HighlighterTargetArea.EXACT_RANGE)
      assertValidHighlighter(added, 0, 0)
    }
    finally {
      model.dispose()
    }
  }

  @Test
  fun `metadata updates preserve highlighter invalidation notifications`(@TestDisposable disposable: Disposable) {
    val document = DocumentImpl("abcdef", true)
    document.addDocumentListener(object : PrioritizedDocumentListener {
      override fun getPriority(): Int = EditorDocumentPriorities.RANGE_MARKER - 1

      override fun documentChanged(event: DocumentEvent) {
        document.modificationStamp++
        document.modificationStamp++
      }
    }, disposable)
    val model = MarkupModelImpl(document)
    try {
      val highlighter = model.addRangeHighlighter(2, 4, 1, null, HighlighterTargetArea.EXACT_RANGE) as RangeHighlighterEx
      val removed = LongArrayList()
      model.addMarkupModelListener(disposable, object : MarkupModelListener {
        override fun afterRemoved(highlighter: RangeHighlighterEx) {
          removed.add(highlighter.id)
        }
      })

      document.deleteString(0, document.textLength)

      Assertions.assertThat(model.containsSnapshotHighlighterId(highlighter.id)).isFalse()
      Assertions.assertThat(removed.toLongArray()).containsExactly(highlighter.id)
      Assertions.assertThat(model.allHighlighters).isEmpty()
    }
    finally {
      model.dispose()
    }
  }

  private fun createWeakHighlighter(model: MarkupModelImpl): WeakReference<RangeHighlighter> {
    val highlighter = model.addRangeHighlighter(2, 4, 1, null, HighlighterTargetArea.EXACT_RANGE)
    return WeakReference(highlighter)
  }

  private fun createAndReleaseHighlighter(
    model: MarkupModelImpl,
    release: (RangeHighlighter) -> Unit,
  ): GCWatcher {
    val highlighter = model.addRangeHighlighter(2, 4, 1, null, HighlighterTargetArea.EXACT_RANGE) as RangeHighlighterEx
    val watcher = GCWatcher.tracking(highlighter)
    Assertions.assertThat(model.allHighlighters).containsExactly(highlighter)
    release(highlighter)
    Assertions.assertThat(model.containsSnapshotHighlighterId(highlighter.id)).isFalse()
    return watcher
  }

  private fun textPatch(startOffset: Int, endOffset: Int, newFragment: String): DocumentPatch {
    return DocumentPatch.simple(
      startOffset = startOffset,
      endOffset = endOffset,
      newFragment = newFragment,
      newModStamp = 1,
      clearLineFlags = false,
    )
  }
}
