// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.ad

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.isRhizomeAdEnabled
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level
import com.intellij.openapi.editor.*
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.ex.*
import com.intellij.openapi.editor.highlighter.EditorHighlighter
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.impl.FocusModeModel
import com.intellij.util.concurrency.ThreadingAssertions
import kotlinx.coroutines.*


@Service(Level.APP)
internal class AdTheManagerImpl(private val coroutineScope: CoroutineScope) : AdTheManager {

  override fun createEditorModel(editor: EditorEx): EditorModel? {
    ThreadingAssertions.assertEventDispatchThread()
    val document = editor.document
    if (isRhizomeAdEnabled && isDocumentGuardedByLock(document)) {
      val docEntity = ThreadLocalRhizomeDB.sharedChange {
        register(AdDocumentEntity)
        AdDocumentEntity.fromString(
          document.immutableCharSequence.toString(),
          document.modificationStamp,
        )
      }
      val adDocument = AdDocument(debugName(document), docEntity)
      val docListener = DocumentSynchronizer(adDocument, coroutineScope)
      document.addDocumentListener(docListener)
      return object : EditorModel {
        override fun getDocument(): DocumentEx = adDocument
        override fun getEditorMarkupModel(): MarkupModelEx = editor.markupModel
        override fun getDocumentMarkupModel(): MarkupModelEx = editor.filteredDocumentMarkupModel
        override fun getHighlighter(): EditorHighlighter = editor.highlighter
        override fun getInlayModel(): InlayModel = editor.inlayModel
        override fun getFoldingModel(): FoldingModelEx = editor.foldingModel
        override fun getSoftWrapModel(): SoftWrapModelEx = editor.softWrapModel
        override fun getCaretModel(): CaretModel = editor.caretModel
        override fun getSelectionModel(): SelectionModel = editor.selectionModel
        override fun getScrollingModel(): ScrollingModel = editor.scrollingModel
        override fun getFocusModel(): FocusModeModel = (editor as EditorImpl).focusModeModel
        override fun isAd(): Boolean = true
        override fun dispose() {
          document.removeDocumentListener(docListener)
          ThreadLocalRhizomeDB.sharedChange {
            docEntity.delete()
          }
        }
      }
    }
    return null
  }

  private fun isDocumentGuardedByLock(document: Document): Boolean {
    return document is DocumentImpl && document.isWriteThreadOnly
  }

  private fun debugName(document: Document): String {
    val hash = Integer.toHexString(System.identityHashCode(document))
    return document.toString().replace("DocumentImpl", "AdDocument@$hash")
  }
}

private class DocumentSynchronizer(
  private val adDocument: AdDocument,
  private val coroutineScope: CoroutineScope,
  private val debugMode: Boolean = false,
) : PrioritizedDocumentListener {

  override fun getPriority(): Int = Integer.MIN_VALUE + 1

  override fun documentChanged(event: DocumentEvent) {
    if (debugMode) {
      coroutineScope.launch {
        delay(1_000)
        withContext(Dispatchers.EDT) {
          syncDoc(adDocument, event)
        }
      }
    } else {
      syncDoc(adDocument, event)
    }
  }

  private fun syncDoc(adDocument: AdDocument, event: DocumentEvent) {
    ThreadLocalRhizomeDB.sharedChange {
      adDocument.replaceString(
        startOffset = event.offset,
        endOffset = event.offset + event.oldLength,
        chars = event.newFragment,
        modStamp = event.document.modificationStamp,
      )
    }
  }
}
