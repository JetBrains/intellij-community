// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.InlayModel
import com.intellij.openapi.editor.InlayProperties
import com.intellij.openapi.editor.ex.DocumentTextPatch
import com.intellij.openapi.editor.impl.marker.PMarker
import com.intellij.openapi.editor.impl.marker.UsePMarkerImplementation
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
@UsePMarkerImplementation
class SnapshotInlayModelTest {
  @Test
  fun `inline inlay resolves in snapshot branches`(): Unit = timeoutRunBlocking {
    val state = withEditor("abcdef") { editor ->
      val document = editor.elfDocument as DocumentImpl
      val initialSnapshot = document.core.snapshot()
      val marker = editor.inlayModel.addInlineElement(2, false, renderer)!! as PMarker
      val shiftedSnapshot = initialSnapshot.applyOp(textPatch(0, 0, "xy"))

      BranchState(
        initialOffset = marker.resolve(initialSnapshot).startOffset,
        shiftedOffset = marker.resolve(shiftedSnapshot).startOffset,
      )
    }

    assertThat(state.initialOffset).isEqualTo(2)
    assertThat(state.shiftedOffset).isEqualTo(4)
  }

  @Test
  fun `inline and after-line-end inlays follow document edits`(): Unit = timeoutRunBlocking {
    val state = withEditor("abcdef") { editor ->
      val inline = editor.inlayModel.addInlineElement(2, false, renderer)!!
      val afterLineEnd = editor.inlayModel.addAfterLineEndElement(4, false, renderer)

      editor.document.insertString(0, "xy")

      EditState(
        usesSnapshotStorage = editor.inlayModel.isUsingSnapshotInlayStorage,
        inlineOffset = inline.offset,
        afterLineEndOffset = afterLineEnd.offset,
        inlineInlays = editor.inlayModel.getInlineElementsInRange(0, editor.document.textLength),
        afterLineEndInlays = editor.inlayModel.getAfterLineEndElementsInRange(0, editor.document.textLength),
      )
    }

    assertThat(state.usesSnapshotStorage).isTrue()
    assertThat(state.inlineOffset).isEqualTo(4)
    assertThat(state.afterLineEndOffset).isEqualTo(6)
    assertThat(state.inlineInlays).hasSize(1)
    assertThat(state.afterLineEndInlays).hasSize(1)
  }

  @Test
  fun `inline inlay sticking follows its text relation`(): Unit = timeoutRunBlocking {
    val offsets = withEditor("abcd") { editor ->
      val beforeInsertion = editor.inlayModel.addInlineElement(2, false, renderer)!!
      val afterInsertion = editor.inlayModel.addInlineElement(2, true, renderer)!!

      editor.document.insertString(2, "x")

      beforeInsertion.offset to afterInsertion.offset
    }

    assertThat(offsets).isEqualTo(2 to 3)
  }

  @Test
  fun `surrogate pair invalidates an inline inlay and disposes its child`(): Unit = timeoutRunBlocking {
    val state = withEditor("\uD83Dx\uDE00") { editor ->
      val inlay = editor.inlayModel.addInlineElement(1, false, renderer)!!
      val child = Disposer.newCheckedDisposable()
      Disposer.register(inlay, child)
      var removalCount = 0
      val listenerDisposable = Disposer.newDisposable()
      try {
        editor.inlayModel.addListener(object : InlayModel.Listener {
          override fun onRemoved(removedInlay: Inlay<*>) {
            if (removedInlay === inlay) removalCount++
          }
        }, listenerDisposable)

        editor.document.deleteString(1, 2)

        DisposalState(inlay.isValid, child.isDisposed, removalCount)
      }
      finally {
        Disposer.dispose(listenerDisposable)
        Disposer.dispose(child)
      }
    }

    assertThat(state.inlayIsValid).isFalse()
    assertThat(state.childIsDisposed).isTrue()
    assertThat(state.removalCount).isEqualTo(1)
  }

  @Test
  fun `explicit disposal disposes an inline inlay child`(): Unit = timeoutRunBlocking {
    val childIsDisposed = withEditor("abc") { editor ->
      val inlay = editor.inlayModel.addInlineElement(1, false, renderer)!!
      val child = Disposer.newCheckedDisposable()
      try {
        Disposer.register(inlay, child)

        inlay.dispose()

        child.isDisposed
      }
      finally {
        Disposer.dispose(child)
      }
    }

    assertThat(childIsDisposed).isTrue()
  }

  @Test
  fun `deletion keeps stationary inline inlays before shifted inlays`(): Unit = timeoutRunBlocking {
    val state = withEditor("abc") { editor ->
      val shifted = editor.inlayModel.addInlineElement(2, false, renderer)!!
      val stationary = editor.inlayModel.addInlineElement(1, false, renderer)!!

      editor.document.deleteString(1, 2)

      Triple(stationary, shifted, editor.inlayModel.getInlineElementsInRange(1, 1))
    }

    assertThat(state.third).containsExactly(state.first, state.second)
  }

  @Test
  fun `block inlay follows document edits and preserves properties`(): Unit = timeoutRunBlocking {
    val state = withEditor("a\nb") { editor ->
      val properties = InlayProperties()
        .showAbove(true)
        .showWhenFolded(true)
        .priority(7)
      val inlay = editor.inlayModel.addBlockElement(2, properties, renderer)!!

      editor.document.insertString(0, "x")

      BlockState(
        usesSnapshotMarker = inlay is PMarker,
        offset = inlay.offset,
        isOnlyRangeResult = editor.inlayModel.getBlockElementsInRange(3, 3) == listOf(inlay),
        isOnlyVisualLineResult = editor.inlayModel.getBlockElementsForVisualLine(1, true) == listOf(inlay),
        isShownWhenFolded = InlayModelImpl.showWhenFolded(inlay),
        priority = inlay.properties.priority,
      )
    }

    assertThat(state.usesSnapshotMarker).isTrue()
    assertThat(state.offset).isEqualTo(3)
    assertThat(state.isOnlyRangeResult).isTrue()
    assertThat(state.isOnlyVisualLineResult).isTrue()
    assertThat(state.isShownWhenFolded).isTrue()
    assertThat(state.priority).isEqualTo(7)
  }

  @Test
  fun `block inlay height measure changes on update`(): Unit = timeoutRunBlocking {
    val state = withEditor("a\nb") { editor ->
      var height = 3
      val blockRenderer = object : EditorCustomElementRenderer {
        override fun calcWidthInPixels(inlay: Inlay<*>): Int = 11

        override fun calcHeightInPixels(inlay: Inlay<*>): Int = height
      }
      val inlay = editor.inlayModel.addBlockElement(0, false, false, 0, blockRenderer)
      val heightBefore = editor.inlayModel.getHeightOfBlockElementsBeforeVisualLine(1, 2, -1)
      val isWidest = editor.inlayModel.widestVisibleBlockInlay === inlay

      height = 7
      inlay.update()

      BlockHeightState(
        heightBefore = heightBefore,
        heightAfter = editor.inlayModel.getHeightOfBlockElementsBeforeVisualLine(1, 2, -1),
        isWidest = isWidest,
      )
    }

    assertThat(state.heightBefore).isEqualTo(3)
    assertThat(state.heightAfter).isEqualTo(7)
    assertThat(state.isWidest).isTrue()
  }

  @Test
  fun `block inlays keep legacy order`(): Unit = timeoutRunBlocking {
    val state = withEditor("text") { editor ->
      val firstAbove = editor.inlayModel.addBlockElement(0, false, true, 0, renderer)
      val secondAbove = editor.inlayModel.addBlockElement(0, false, true, 0, renderer)
      val firstBelow = editor.inlayModel.addBlockElement(0, false, false, 0, renderer)
      val secondBelow = editor.inlayModel.addBlockElement(0, false, false, 0, renderer)

      Triple(
        editor.inlayModel.getBlockElementsInRange(0, 0) == listOf(firstAbove, secondAbove, firstBelow, secondBelow),
        editor.inlayModel.getBlockElementsForVisualLine(0, true) == listOf(secondAbove, firstAbove),
        editor.inlayModel.getBlockElementsForVisualLine(0, false) == listOf(firstBelow, secondBelow),
      )
    }

    assertThat(state.first).isTrue()
    assertThat(state.second).isTrue()
    assertThat(state.third).isTrue()
  }

  @Test
  fun `explicit disposal removes a block inlay`(): Unit = timeoutRunBlocking {
    val state = withEditor("abc") { editor ->
      val inlay = editor.inlayModel.addBlockElement(1, false, false, 0, renderer)
      val child = Disposer.newCheckedDisposable()
      try {
        Disposer.register(inlay, child)

        inlay.dispose()

        BlockDisposalState(
          inlayIsValid = inlay.isValid,
          childIsDisposed = child.isDisposed,
          hasBlockElements = editor.inlayModel.hasBlockElements(),
        )
      }
      finally {
        Disposer.dispose(child)
      }
    }

    assertThat(state.inlayIsValid).isFalse()
    assertThat(state.childIsDisposed).isTrue()
    assertThat(state.hasBlockElements).isFalse()
  }

  @Test
  @UsePMarkerImplementation(false)
  fun `disabled snapshot marker implementation uses inlay trees`(): Unit = timeoutRunBlocking {
    val usesSnapshotStorage = withEditor("abc") { editor ->
      editor.inlayModel.isUsingSnapshotInlayStorage
    }

    assertThat(usesSnapshotStorage).isFalse()
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
    val initialOffset: Int,
    val shiftedOffset: Int,
  )

  private data class EditState(
    val usesSnapshotStorage: Boolean,
    val inlineOffset: Int,
    val afterLineEndOffset: Int,
    val inlineInlays: List<Inlay<*>>,
    val afterLineEndInlays: List<Inlay<*>>,
  )

  private data class DisposalState(
    val inlayIsValid: Boolean,
    val childIsDisposed: Boolean,
    val removalCount: Int,
  )

  private data class BlockState(
    val usesSnapshotMarker: Boolean,
    val offset: Int,
    val isOnlyRangeResult: Boolean,
    val isOnlyVisualLineResult: Boolean,
    val isShownWhenFolded: Boolean,
    val priority: Int,
  )

  private data class BlockHeightState(
    val heightBefore: Int,
    val heightAfter: Int,
    val isWidest: Boolean,
  )

  private data class BlockDisposalState(
    val inlayIsValid: Boolean,
    val childIsDisposed: Boolean,
    val hasBlockElements: Boolean,
  )

  private val renderer = EditorCustomElementRenderer { 1 }
}
