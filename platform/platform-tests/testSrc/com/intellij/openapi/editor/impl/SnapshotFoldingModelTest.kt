// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.editor.CustomFoldRegion
import com.intellij.openapi.editor.CustomFoldRegionRenderer
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.FoldRegion
import com.intellij.openapi.editor.ex.DocumentPatch
import com.intellij.openapi.editor.ex.FoldingListener
import com.intellij.openapi.editor.impl.marker.PMarkerInsertionRoot
import com.intellij.openapi.editor.impl.marker.PMarkerResolution
import com.intellij.openapi.editor.impl.marker.PMarkerRoot
import com.intellij.openapi.editor.impl.marker.SnapshotMarkerReference
import com.intellij.openapi.editor.impl.marker.SnapshotRangeMarkerImpl
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.TextRangeScalarUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.UsePMarkerImplementation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.awt.Graphics2D
import java.awt.geom.Rectangle2D

@Suppress("SuspiciousPackagePrivateAccess")
@TestApplication
@UsePMarkerImplementation
class SnapshotFoldingModelTest {
  @Test
  fun `caret batch preserves fold snapshots and finishes after an exception`(): Unit = timeoutRunBlocking {
    val state = withEditor("0123456789\n0123456789\n0123456789") { editor ->
      val first = addFoldRegion(editor, 1, 8) as SnapshotRangeMarkerImpl
      val second = addFoldRegion(editor, 12, 19) as SnapshotRangeMarkerImpl
      editor.caretModel.addCaret(editor.offsetToVisualPosition(editor.document.textLength))!!
      val store = editor.foldingModel.rootStore()
      val document = editor.document as DocumentImpl
      val initial = store.root(document.core.snapshot())!!
      val roots = ArrayList<PMarkerRoot>()
      val failure = runCatching {
        editor.caretModel.runBatchCaretOperation {
          document.insertString(4, "a")
          roots.add(store.root(document.core.snapshot())!!)
          check(editor.foldingModel.getFoldRegion(1, 9) === first)
          store.runInBatch {
            document.insertString(16, "b\n")
            roots.add(store.root(document.core.snapshot())!!)
            check(editor.foldingModel.getFoldRegion(13, 22) === second)
          }
          check(store.root(document.core.snapshot()) is PMarkerInsertionRoot)
          throw IllegalStateException("test failure")
        }
      }.exceptionOrNull()
      val finished = store.root(document.core.snapshot())!!
      editor.caretModel.runBatchCaretOperation { document.insertString(25, "x") }
      BatchState(
        failure = failure?.message,
        bufferedRoots = roots.map { it is PMarkerInsertionRoot },
        finished = finished !is PMarkerInsertionRoot,
        ranges = listOf(first.resolve(initial), second.resolve(initial), second.resolve(roots[0]), second.resolve(roots[1])),
        finalRange = second.textRange,
      )
    }
    assertThat(state.failure).isEqualTo("test failure")
    assertThat(state.bufferedRoots).containsExactly(true, true)
    assertThat(state.finished).isTrue()
    assertThat(state.ranges).containsExactly(
      TextRangeScalarUtil.toScalarRange(1, 8), TextRangeScalarUtil.toScalarRange(12, 19),
      TextRangeScalarUtil.toScalarRange(13, 20), TextRangeScalarUtil.toScalarRange(13, 22),
    )
    assertThat(state.finalRange).isEqualTo(TextRange(13, 22))
  }

  @Test
  fun `fold batch falls back when an insertion forms a surrogate pair at a boundary`(): Unit = timeoutRunBlocking {
    val range = withEditor("a\uDE00bcde") { editor ->
      val region = addFoldRegion(editor, 1, 5)
      editor.caretModel.addCaret(editor.offsetToVisualPosition(editor.document.textLength))!!
      editor.caretModel.runBatchCaretOperation {
        editor.document.insertString(3, "x")
        editor.document.insertString(1, "\uD83D")
      }
      region.textRange
    }
    assertThat(range).isEqualTo(TextRange(1, 7))
  }

  @Test
  fun `custom folds validate line boundaries after buffered insertions`(): Unit = timeoutRunBlocking {
    val state = withEditor("a\nb\nc") { editor ->
      lateinit var region: CustomFoldRegion
      editor.foldingModel.runBatchFoldingOperation {
        region = editor.foldingModel.addCustomLinesFolding(0, 1, customRenderer)!!
      }
      editor.caretModel.addCaret(editor.offsetToVisualPosition(editor.document.textLength))!!
      editor.caretModel.runBatchCaretOperation {
        editor.document.insertString(1, "q\n")
        check(region.textRange == TextRange(0, 5))
        editor.document.insertString(5, "x")
      }
      region.isValid to editor.foldingModel.allFoldRegions.size
    }
    assertThat(state.first).isFalse()
    assertThat(state.second).isZero()
  }

  @Test
  fun `fold region resolves in snapshot branches`(): Unit = timeoutRunBlocking {
    val state = withEditor("abcdef") { editor ->
      val document = editor.elfDocument as DocumentImpl
      val initialSnapshot = document.core.snapshot()
      val region = addFoldRegion(editor, 1, 4) as SnapshotRangeMarkerImpl
      val rootStore = editor.foldingModel.rootStore()
      val shiftedSnapshot = document.snapshotMarkerStores.applyPatch(initialSnapshot, textPatch(0, 0, "xy"))
      val shiftedResolution = region.resolve(rootStore.rootReference(shiftedSnapshot).get())

      BranchState(
        usesSnapshotStorage = editor.foldingModel.isUsingSnapshotFoldingStorage,
        initialStart = region.startOffset,
        shiftedStart = PMarkerResolution.startOffset(shiftedResolution),
      )
    }

    assertThat(state.usesSnapshotStorage).isTrue()
    assertThat(state.initialStart).isEqualTo(1)
    assertThat(state.shiftedStart).isEqualTo(3)
  }

  @Test
  fun `fold region follows edits and remains queryable`(): Unit = timeoutRunBlocking {
    val state = withEditor("0123456789") { editor ->
      val region = addFoldRegion(editor, 2, 7)

      editor.document.insertString(0, "ab")
      val changedOutsideRegion = editor.foldingModel.hasDocumentRegionChangedFor(region)
      editor.document.insertString(5, "x")

      EditState(
        startOffset = region.startOffset,
        endOffset = region.endOffset,
        foundRegion = editor.foldingModel.getFoldRegion(4, 10),
        changedOutsideRegion = changedOutsideRegion,
        documentRegionChanged = editor.foldingModel.hasDocumentRegionChangedFor(region),
      )
    }

    assertThat(state.startOffset).isEqualTo(4)
    assertThat(state.endOffset).isEqualTo(10)
    assertThat(state.foundRegion).isNotNull()
    assertThat(state.changedOutsideRegion).isFalse()
    assertThat(state.documentRegionChanged).isTrue()
  }

  @Test
  fun `regions with equal starts remain ordered after a shift`(): Unit = timeoutRunBlocking {
    val state = withEditor("012345678901234567890123456789") { editor ->
      editor.foldingModel.runBatchFoldingOperation {
        editor.foldingModel.addFoldRegion(21, 25, "...")!!
        editor.foldingModel.addFoldRegion(12, 19, "...")!!
        editor.foldingModel.addFoldRegion(12, 17, "...")!!.isExpanded = false
        editor.foldingModel.addFoldRegion(0, 10, "...")!!
        editor.foldingModel.addFoldRegion(0, 5, "...")!!.isExpanded = false
      }

      editor.document.insertString(0, "ab")

      RegionOrderState(
        allRegions = editor.foldingModel.allFoldRegions.map { it.textRange },
        visibleRegions = editor.foldingModel.fetchVisible()!!.map { it.textRange },
      )
    }

    assertThat(state.allRegions).containsExactly(
      TextRange(2, 7), TextRange(2, 12), TextRange(14, 19), TextRange(14, 21), TextRange(23, 27),
    )
    assertThat(state.visibleRegions).containsExactly(
      TextRange(23, 27), TextRange(14, 21), TextRange(14, 19), TextRange(2, 12), TextRange(2, 7),
    )
  }

  @Test
  fun `full traversal reuses the buffer across groups of different sizes`(): Unit = timeoutRunBlocking {
    val regions = withEditor("01234567890123456789012345678901234567890123456789") { editor ->
      editor.foldingModel.runBatchFoldingOperation {
        for ((start, count) in listOf(0 to 5, 20 to 1, 40 to 3)) {
          for (length in count + 1 downTo 2) {
            editor.foldingModel.addFoldRegion(start, start + length, "...")!!
          }
        }
      }
      editor.document.insertString(0, "ab")

      editor.foldingModel.allFoldRegions.map { it.textRange }
    }

    assertThat(regions).containsExactly(
      TextRange(2, 4), TextRange(2, 5), TextRange(2, 6), TextRange(2, 7), TextRange(2, 8),
      TextRange(22, 24),
      TextRange(42, 44), TextRange(42, 45), TextRange(42, 46),
    )
  }

  @Test
  fun `regular region precedes a custom region with the same range`(): Unit = timeoutRunBlocking {
    val (customRegions, foundRegularRegion) = withEditor("a\nb\nc") { editor ->
      lateinit var regularRegion: FoldRegion
      editor.foldingModel.runBatchFoldingOperation {
        editor.foldingModel.addCustomLinesFolding(0, 1, customRenderer)!!
        regularRegion = editor.foldingModel.addFoldRegion(0, 3, "...")!!
      }

      editor.foldingModel.allFoldRegions.map { it is CustomFoldRegion } to
        (editor.foldingModel.getFoldRegion(0, 3) === regularRegion)
    }

    assertThat(customRegions).containsExactly(false, true)
    assertThat(foundRegularRegion).isTrue()
  }

  @Test
  fun `editor disposal removes all regions in order after a shift`(@TestDisposable disposable: Disposable): Unit = timeoutRunBlocking {
    val disposedRegions = ArrayList<FoldRegion>()
    val regions = withEditor("a\nb\nc\nd\ne") { editor ->
      lateinit var customRegion: CustomFoldRegion
      lateinit var regularRegion: FoldRegion
      lateinit var outerRegion: FoldRegion
      lateinit var laterRegion: FoldRegion
      editor.foldingModel.runBatchFoldingOperation {
        laterRegion = editor.foldingModel.addFoldRegion(6, 9, "...")!!
        customRegion = editor.foldingModel.addCustomLinesFolding(0, 2, customRenderer)!!
        outerRegion = editor.foldingModel.addFoldRegion(0, 5, "...")!!
        regularRegion = editor.foldingModel.addFoldRegion(0, 3, "...")!!
      }
      editor.document.insertString(0, "prefix\n")
      editor.foldingModel.addListener(object : FoldingListener {
        override fun beforeFoldRegionDisposed(region: FoldRegion) {
          disposedRegions.add(region)
        }
      }, disposable)

      listOf(regularRegion, outerRegion, customRegion, laterRegion)
    }

    assertThat(disposedRegions).containsExactlyElementsOf(regions)
    assertThat(regions.map { it.isValid }).containsOnly(false)
  }

  @Test
  fun `fold queries stop before resolving later regions`(): Unit = timeoutRunBlocking {
    val (foundRegion, intersectsRegion, resolutionCount) = withEditor("0123456789") { editor ->
      val firstRegion = addFoldRegion(editor, 0, 10)
      val laterRegion = addFoldRegion(editor, 3, 5) as SnapshotRangeMarkerImpl
      val rootStore = editor.foldingModel.rootStore()
      val snapshot = (editor.document as DocumentImpl).core.snapshot()
      var resolutionCount = 0
      rootStore.updateRoot(snapshot) { root ->
        object : PMarkerRoot by root {
          override val resolutionCacheIdentity: Any = Any()

          override fun resolve(handle: SnapshotMarkerReference<*>): Long {
            if (handle === laterRegion.markerHandle) resolutionCount++
            return root.resolve(handle)
          }
        }
      }

      Triple(
        editor.foldingModel.getFoldRegion(0, 10) === firstRegion,
        editor.foldingModel.intersectsRegion(1, 10),
        resolutionCount,
      )
    }

    assertThat(foundRegion).isTrue()
    assertThat(intersectsRegion).isTrue()
    assertThat(resolutionCount).isZero()
  }

  @Test
  fun `collapsed region survives an edit that creates a duplicate`(): Unit = timeoutRunBlocking {
    val state = withEditor("01234567890123456789") { editor ->
      lateinit var collapsed: FoldRegion
      lateinit var expanded: FoldRegion
      editor.foldingModel.runBatchFoldingOperation {
        collapsed = editor.foldingModel.addFoldRegion(0, 10, "collapsed")!!
        collapsed.isExpanded = false
        expanded = editor.foldingModel.addFoldRegion(1, 10, "expanded")!!
      }

      editor.document.deleteString(0, 1)

      DuplicateState(
        collapsedIsValid = collapsed.isValid,
        expandedIsValid = expanded.isValid,
        allRegions = editor.foldingModel.allFoldRegions.toList(),
      )
    }

    assertThat(state.collapsedIsValid).isTrue()
    assertThat(state.expandedIsValid).isFalse()
    assertThat(state.allRegions).hasSize(1)
    assertThat(state.allRegions.single().placeholderText).isEqualTo("collapsed")
  }

  @Test
  fun `visible duplicate leaves the cache`(): Unit = timeoutRunBlocking {
    val (survivor, visibleRegions) = withEditor("01234567890123456789") { editor ->
      val model = editor.foldingModel
      lateinit var collapsed: FoldRegion
      model.runBatchFoldingOperation {
        model.addFoldRegion(0, 10, "expanded")!!
        collapsed = model.addFoldRegion(1, 10, "collapsed")!!
        collapsed.isExpanded = false
      }
      model.fetchVisible()

      edtWriteAction {
        editor.document.deleteString(0, 1)
      }

      collapsed to model.fetchVisible()!!.toList()
    }

    assertThat(visibleRegions).containsExactly(survivor)
  }

  @Test
  fun `deleted fold region leaves the cache after folding is enabled`(): Unit = timeoutRunBlocking {
    val visibleRegions = withEditor("0123456789") { editor ->
      val model = editor.foldingModel
      model.runBatchFoldingOperation {
        model.addFoldRegion(2, 7, "...")!!
      }
      model.fetchVisible()

      model.setFoldingEnabled(false)
      edtWriteAction {
        editor.document.deleteString(1, 8)
      }
      model.setFoldingEnabled(true)
      model.updateCachedOffsets()

      model.fetchVisible()!!.toList()
    }

    assertThat(visibleRegions).isEmpty()
  }

  @Test
  fun `custom fold region invalidates outside line boundaries`(): Unit = timeoutRunBlocking {
    val state = withEditor("a\nb\nc") { editor ->
      val listenerDisposable = Disposer.newDisposable()
      try {
        var disposalCount = 0
        editor.foldingModel.addListener(object : FoldingListener {
          override fun beforeFoldRegionDisposed(region: FoldRegion) {
            disposalCount++
          }
        }, listenerDisposable)
        lateinit var region: CustomFoldRegion
        editor.foldingModel.runBatchFoldingOperation {
          region = editor.foldingModel.addCustomLinesFolding(0, 1, customRenderer)!!
        }

        editor.document.insertString(region.endOffset, "x")

        CustomRegionState(region.isValid, disposalCount, editor.foldingModel.allFoldRegions.size)
      }
      finally {
        Disposer.dispose(listenerDisposable)
      }
    }

    assertThat(state.isValid).isFalse()
    assertThat(state.disposalCount).isEqualTo(1)
    assertThat(state.regionCount).isZero()
  }

  @Test
  fun `custom fold region invalidates after another region aligns to a new line break`(): Unit = timeoutRunBlocking {
    val state = withEditor("") { editor ->
      val document = editor.document as DocumentImpl
      document.setAcceptSlashR(true)
      document.insertString(0, "a\rb\nc")

      lateinit var customRegion: CustomFoldRegion
      lateinit var regularRegion: FoldRegion
      editor.foldingModel.runBatchFoldingOperation {
        customRegion = editor.foldingModel.addCustomLinesFolding(0, 0, customRenderer)!!
        regularRegion = editor.foldingModel.addFoldRegion(3, 5, "...")!!
        regularRegion.isExpanded = false
      }

      document.deleteString(2, 3)

      ComplexLineBreakState(
        customRegionIsValid = customRegion.isValid,
        regularRegionRange = regularRegion.textRange,
        regionCount = editor.foldingModel.allFoldRegions.size,
      )
    }

    assertThat(state.customRegionIsValid).isFalse()
    assertThat(state.regularRegionRange).isEqualTo(TextRange(1, 4))
    assertThat(state.regionCount).isEqualTo(1)
  }

  private suspend fun <T> withEditor(text: String, action: suspend (EditorImpl) -> T): T {
    return withContext(Dispatchers.EDT) {
      val editorFactory = EditorFactory.getInstance()
      val editor = editorFactory.createEditor(DocumentImpl(text, true)) as EditorImpl
      try {
        action(editor)
      }
      finally {
        editorFactory.releaseEditor(editor)
      }
    }
  }

  private fun addFoldRegion(editor: EditorImpl, startOffset: Int, endOffset: Int): FoldRegion {
    lateinit var region: FoldRegion
    editor.foldingModel.runBatchFoldingOperation {
      region = editor.foldingModel.addFoldRegion(startOffset, endOffset, "...")!!
    }
    return region
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

  private data class BatchState(
    val failure: String?,
    val bufferedRoots: List<Boolean>,
    val finished: Boolean,
    val ranges: List<Long>,
    val finalRange: TextRange,
  )

  private data class BranchState(
    val usesSnapshotStorage: Boolean,
    val initialStart: Int,
    val shiftedStart: Int,
  )

  private data class EditState(
    val startOffset: Int,
    val endOffset: Int,
    val foundRegion: FoldRegion?,
    val changedOutsideRegion: Boolean,
    val documentRegionChanged: Boolean,
  )

  private data class RegionOrderState(
    val allRegions: List<TextRange>,
    val visibleRegions: List<TextRange>,
  )

  private data class DuplicateState(
    val collapsedIsValid: Boolean,
    val expandedIsValid: Boolean,
    val allRegions: List<FoldRegion>,
  )

  private data class CustomRegionState(
    val isValid: Boolean,
    val disposalCount: Int,
    val regionCount: Int,
  )

  private data class ComplexLineBreakState(
    val customRegionIsValid: Boolean,
    val regularRegionRange: TextRange,
    val regionCount: Int,
  )

  private val customRenderer: CustomFoldRegionRenderer = object : CustomFoldRegionRenderer {
    override fun calcWidthInPixels(region: CustomFoldRegion): Int = 10

    override fun calcHeightInPixels(region: CustomFoldRegion): Int = 10

    override fun paint(region: CustomFoldRegion, graphics: Graphics2D, targetRegion: Rectangle2D, textAttributes: TextAttributes) {
    }
  }
}
