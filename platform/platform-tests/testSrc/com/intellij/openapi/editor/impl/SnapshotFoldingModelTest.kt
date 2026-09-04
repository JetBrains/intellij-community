// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.CustomFoldRegion
import com.intellij.openapi.editor.CustomFoldRegionRenderer
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.FoldRegion
import com.intellij.openapi.editor.ex.DocumentTextPatch
import com.intellij.openapi.editor.ex.FoldingListener
import com.intellij.openapi.editor.impl.marker.PMarker
import com.intellij.openapi.editor.impl.marker.UsePMarkerImplementation
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.awt.Graphics2D
import java.awt.geom.Rectangle2D

@TestApplication
@UsePMarkerImplementation
class SnapshotFoldingModelTest {
  @Test
  fun `fold region resolves in snapshot branches`(): Unit = timeoutRunBlocking {
    val state = withEditor("abcdef") { editor ->
      val document = editor.elfDocument as DocumentImpl
      val initialSnapshot = document.core.snapshot()
      val region = addFoldRegion(editor, 1, 4) as PMarker
      val shiftedSnapshot = initialSnapshot.applyOp(textPatch(0, 0, "xy"))

      BranchState(
        usesSnapshotStorage = editor.foldingModel.isUsingSnapshotFoldingStorage,
        initialStart = region.resolve(initialSnapshot).startOffset,
        shiftedStart = region.resolve(shiftedSnapshot).startOffset,
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
  @UsePMarkerImplementation(false)
  fun `disabled snapshot marker implementation uses the legacy fold tree`(): Unit = timeoutRunBlocking {
    val state = withEditor("abcdef") { editor ->
      val region = addFoldRegion(editor, 1, 4)
      editor.foldingModel.isUsingSnapshotFoldingStorage to region
    }

    assertThat(state.first).isFalse()
    assertThat(state.second).isInstanceOf(FoldRegionImpl::class.java)
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

  private fun textPatch(startOffset: Int, endOffset: Int, newFragment: String): DocumentTextPatch {
    return DocumentTextPatch.simple(
      startOffset = startOffset,
      endOffset = endOffset,
      newFragment = newFragment,
      newModStamp = 1,
      clearLineFlags = false,
    )
  }

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

  private val customRenderer = object : CustomFoldRegionRenderer {
    override fun calcWidthInPixels(region: CustomFoldRegion): Int = 10

    override fun calcHeightInPixels(region: CustomFoldRegion): Int = 10

    override fun paint(region: CustomFoldRegion, graphics: Graphics2D, targetRegion: Rectangle2D, textAttributes: TextAttributes) {
    }
  }
}
