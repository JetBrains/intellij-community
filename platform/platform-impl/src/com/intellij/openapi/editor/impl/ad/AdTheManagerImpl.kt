// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.ad

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.isRhizomeAdEnabled
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level
import com.intellij.openapi.editor.*
import com.intellij.openapi.editor.ex.*
import com.intellij.openapi.editor.highlighter.EditorHighlighter
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.impl.FocusModeModel
import com.intellij.openapi.util.Disposer
import com.intellij.platform.kernel.withKernel
import com.intellij.util.concurrency.ThreadingAssertions
import com.jetbrains.rhizomedb.ChangeScope
import fleet.kernel.change
import fleet.kernel.shared
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking


@Service(Level.PROJECT)
internal class AdTheManagerImpl(private val coroutineScope: CoroutineScope) : AdTheManager {

  override fun createEditorModel(editor: EditorEx): EditorModel? {
    ThreadingAssertions.assertEventDispatchThread()
    if (isRhizomeAdEnabled && editor is EditorImpl && isDocumentGuardedByLock(editor.document)) {
      val modStampBefore = editor.document.modificationStamp
      @Suppress("RAW_RUN_BLOCKING")
      val editorModel = runBlocking {
        withKernel {
          change {
            shared {
              createEditorModelImpl(editor)
            }
          }
        }
      }

      ThreadLocalRhizomeDB.setThreadLocalDb(ThreadLocalRhizomeDB.lastKnownDb())

      val documentSynchronizer = AdDocumentSynchronizer(editorModel.document as AdDocument, coroutineScope) { repaintEditor(editor) }
      editor.document.addDocumentListener(documentSynchronizer, documentSynchronizer)

      Disposer.register(editorModel, documentSynchronizer)
      Disposer.register(editorModel, editorModel.editorMarkupModel as Disposable)
      Disposer.register(editorModel, editorModel.documentMarkupModel as Disposable)
      Disposer.register(editor.disposable, editorModel)

      assert(editor.document.modificationStamp == modStampBefore)

      return editorModel
    }
    return null
  }

  private fun ChangeScope.createEditorModelImpl(editor: EditorImpl): EditorModel {
    register(AdDocumentEntity)
    val document = editor.document
    val entity = AdDocumentEntity.fromString(
      document.immutableCharSequence.toString(),
      document.modificationStamp,
    )
    val adDocument = AdDocument(debugName(document), entity)

    fun createMarkup(markupModel: MarkupModelEx) = AdMarkupModel.fromMarkup(
      markupModel,
      adDocument,
      coroutineScope
    ) { repaintEditor(editor) }

    val editorMarkup = createMarkup(editor.markupModel)
    val documentMarkup = createMarkup(editor.filteredDocumentMarkupModel)

    return object : EditorModel {
      override fun getDocument(): DocumentEx = adDocument
      override fun getEditorMarkupModel(): MarkupModelEx = editorMarkup
      override fun getDocumentMarkupModel(): MarkupModelEx = documentMarkup
      override fun getHighlighter(): EditorHighlighter = editor.highlighter
      override fun getInlayModel(): InlayModel = editor.inlayModel
      override fun getFoldingModel(): FoldingModelEx = editor.foldingModel
      override fun getSoftWrapModel(): SoftWrapModelEx = editor.softWrapModel
      override fun getCaretModel(): CaretModel = editor.caretModel
      override fun getSelectionModel(): SelectionModel = editor.selectionModel
      override fun getScrollingModel(): ScrollingModel = editor.scrollingModel
      override fun getFocusModel(): FocusModeModel = editor.focusModeModel
      override fun isAd(): Boolean = true
      override fun dispose() {
        coroutineScope.launch {
          withKernel {
            change {
              shared {
                entity.delete()
              }
            }
          }
        }
      }
    }
  }

  private fun repaintEditor(editor: EditorImpl) {
    ThreadingAssertions.assertEventDispatchThread()
    if (!(editor.isDisposed)) {
      editor.repaintToScreenBottom(0) // TODO repaint partially
    }
  }

  private fun isDocumentGuardedByLock(document: Document): Boolean {
    return document is DocumentImpl && document.isWriteThreadOnly
  }

  private fun debugName(document: Document): String {
    val hash = Integer.toHexString(System.identityHashCode(document))
    return document.toString().replace("DocumentImpl", "AdDocument@$hash")
  }
}
