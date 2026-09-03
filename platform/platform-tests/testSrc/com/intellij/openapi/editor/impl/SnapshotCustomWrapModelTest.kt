// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.CustomWrap
import com.intellij.openapi.editor.CustomWrapModel
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ex.DocumentEx
import com.intellij.openapi.editor.ex.DocumentTextPatch
import com.intellij.openapi.editor.impl.marker.PMarker
import com.intellij.openapi.editor.impl.marker.SnapshotRangeMarkerImpl
import com.intellij.openapi.editor.impl.marker.UsePMarkerImplementation
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.ref.GCUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.lang.ref.WeakReference

@TestApplication
@UsePMarkerImplementation
@RegistryKey(key = "editor.use.new.soft.wraps.impl", value = "true")
@RegistryKey(key = "editor.custom.soft.wraps.support.enabled", value = "true")
class SnapshotCustomWrapModelTest {
  @Test
  fun `custom wrap resolves in snapshot branches`(): Unit = timeoutRunBlocking {
    val state = withEditor("abc\ndef") { editor ->
      val document = editor.elfDocument as DocumentImpl
      val initialSnapshot = document.core.snapshot()
      val wrap = editor.customWrapModel.runBatchMutation { addWrap(5) }!!
      val snapshotWrap = wrap as PMarker
      val shiftedBranch = initialSnapshot.applyOp(textPatch(0, 0, "x"))
      val invalidBranch = initialSnapshot.applyOp(textPatch(4, 5, ""))
      val initialResolution = snapshotWrap.resolve(initialSnapshot)
      val shiftedResolution = snapshotWrap.resolve(shiftedBranch)
      val invalidResolution = snapshotWrap.resolve(invalidBranch)
      BranchState(
        wrap = wrap,
        initialRange = initialResolution.startOffset to initialResolution.endOffset,
        shiftedRange = shiftedResolution.startOffset to shiftedResolution.endOffset,
        invalidRange = invalidResolution.startOffset to invalidResolution.endOffset,
        invalidBranchIsValid = invalidResolution.isValid,
        initialBranchIsValid = initialResolution.isValid,
      )
    }

    assertThat(state.wrap).isInstanceOf(SnapshotRangeMarkerImpl::class.java)
    assertThat(state.initialRange).isEqualTo(5 to 5)
    assertThat(state.shiftedRange).isEqualTo(6 to 6)
    assertThat(state.invalidRange).isEqualTo(4 to 4)
    assertThat(state.invalidBranchIsValid).isFalse()
    assertThat(state.initialBranchIsValid).isTrue()
  }

  @Test
  fun `document edit removes an invalid custom wrap once`(): Unit = timeoutRunBlocking {
    val state = withEditor("abc\ndef") { editor ->
      val model = editor.customWrapModel
      val removedOffsets = ArrayList<Int>()
      val disposable = Disposer.newDisposable()
      try {
        model.addListener(object : CustomWrapModel.Listener {
          override fun customWrapRemoved(wrap: CustomWrap) {
            removedOffsets.add(wrap.offset)
          }
        }, disposable)
        val wrap = model.runBatchMutation { addWrap(5) }!!

        editor.document.deleteString(4, 5)

        EditState(
          wrapOffset = wrap.offset,
          remainingWraps = model.getWraps(),
          removedOffsets = removedOffsets.toList(),
          secondRemoval = model.runBatchMutation { removeWrap(wrap) },
        )
      }
      finally {
        Disposer.dispose(disposable)
      }
    }

    assertThat(state.wrapOffset).isEqualTo(4)
    assertThat(state.remainingWraps).isEmpty()
    assertThat(state.removedOffsets).containsExactly(4)
    assertThat(state.secondRemoval).isFalse()
  }

  @Test
  fun `move removes a custom wrap that reaches a line start`(): Unit = timeoutRunBlocking {
    val state = withEditor("abc\ndefghi") { editor ->
      val model = editor.customWrapModel
      val events = ArrayList<String>()
      val disposable = Disposer.newDisposable()
      try {
        model.addListener(object : CustomWrapModel.Listener {
          override fun customWrapAdded(wrap: CustomWrap) {
            events.add("add:${wrap.offset}")
          }

          override fun customWrapRemoved(wrap: CustomWrap) {
            events.add("remove:${wrap.offset}")
          }
        }, disposable)
        model.runBatchMutation { addWrap(6) }

        (editor.document as DocumentEx).moveText(6, 8, 4)

        MoveState(editor.document.text, model.getWraps(), events.toList())
      }
      finally {
        Disposer.dispose(disposable)
      }
    }

    assertThat(state.text).isEqualTo("abc\nfgdehi")
    assertThat(state.remainingWraps).isEmpty()
    assertThat(state.events).containsExactly("add:6", "remove:4")
  }

  @Test
  fun `editors keep separate custom wraps`(): Unit = timeoutRunBlocking {
    val document = DocumentImpl("abcdef", true)
    val state = withEditor(document) { firstEditor ->
      withEditor(document) { secondEditor ->
        val firstWrap = firstEditor.customWrapModel.runBatchMutation { addWrap(2) }!!
        val firstBefore = firstEditor.customWrapModel.getWraps()
        val secondBefore = secondEditor.customWrapModel.getWraps()
        val secondWrap = secondEditor.customWrapModel.runBatchMutation { addWrap(4) }!!
        EditorState(
          firstWrap = firstWrap,
          secondWrap = secondWrap,
          firstBefore = firstBefore,
          secondBefore = secondBefore,
          firstAfter = firstEditor.customWrapModel.getWraps(),
          secondAfter = secondEditor.customWrapModel.getWraps(),
        )
      }
    }

    assertThat(state.firstBefore).containsExactly(state.firstWrap)
    assertThat(state.secondBefore).isEmpty()
    assertThat(state.firstAfter).containsExactly(state.firstWrap)
    assertThat(state.secondAfter).containsExactly(state.secondWrap)
  }

  @Test
  fun `snapshot root retains a custom wrap`(): Unit = timeoutRunBlocking {
    val state = withEditor("abcdef") { editor ->
      val wrapReference = createWeakWrap(editor)

      GCUtil.tryGcSoftlyReachableObjects()

      RetentionState(wrapReference.get(), editor.customWrapModel.getWraps())
    }

    assertThat(state.wrap).isNotNull()
    assertThat(state.currentWraps).containsExactly(state.wrap)
  }

  @Test
  @UsePMarkerImplementation(false)
  fun `disabled snapshot marker implementation uses a legacy custom wrap`(): Unit = timeoutRunBlocking {
    val wrap = withEditor("abcdef") { editor ->
      editor.customWrapModel.runBatchMutation { addWrap(2) }
    }

    assertThat(wrap).isNotInstanceOf(SnapshotRangeMarkerImpl::class.java)
    assertThat(wrap?.javaClass?.name).isEqualTo("com.intellij.openapi.editor.impl.customwrap.CustomWrapImpl")
  }

  private suspend fun <T> withEditor(text: String, action: suspend (EditorImpl) -> T): T {
    return withEditor(DocumentImpl(text, true), action)
  }

  private suspend fun <T> withEditor(document: DocumentImpl, action: suspend (EditorImpl) -> T): T {
    return withContext(Dispatchers.EDT) {
      val editorFactory = EditorFactory.getInstance()
      val editor = editorFactory.createEditor(document) as EditorImpl
      try {
        action(editor)
      }
      finally {
        editorFactory.releaseEditor(editor)
      }
    }
  }

  private data class BranchState(
    val wrap: CustomWrap,
    val initialRange: Pair<Int, Int>,
    val shiftedRange: Pair<Int, Int>,
    val invalidRange: Pair<Int, Int>,
    val invalidBranchIsValid: Boolean,
    val initialBranchIsValid: Boolean,
  )

  private data class EditState(
    val wrapOffset: Int,
    val remainingWraps: List<CustomWrap>,
    val removedOffsets: List<Int>,
    val secondRemoval: Boolean,
  )

  private data class EditorState(
    val firstWrap: CustomWrap,
    val secondWrap: CustomWrap,
    val firstBefore: List<CustomWrap>,
    val secondBefore: List<CustomWrap>,
    val firstAfter: List<CustomWrap>,
    val secondAfter: List<CustomWrap>,
  )

  private data class MoveState(
    val text: String,
    val remainingWraps: List<CustomWrap>,
    val events: List<String>,
  )

  private data class RetentionState(
    val wrap: CustomWrap?,
    val currentWraps: List<CustomWrap>,
  )

  private fun createWeakWrap(editor: EditorImpl): WeakReference<CustomWrap> {
    return WeakReference(editor.customWrapModel.runBatchMutation { addWrap(2) }!!)
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
