// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.ad

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.isRhizomeAdEnabled
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.CaretModel
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ScrollingModel
import com.intellij.openapi.editor.SelectionModel
import com.intellij.openapi.editor.ex.*
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.editor.highlighter.EditorHighlighter
import com.intellij.openapi.editor.impl.*
import com.intellij.openapi.editor.impl.ad.document.AdDocument
import com.intellij.openapi.editor.impl.ad.document.AdDocumentManager
import com.intellij.openapi.editor.impl.ad.markup.AdDocumentMarkupManager
import com.intellij.openapi.editor.impl.ad.markup.AdEditorMarkupManager
import com.intellij.openapi.editor.impl.ad.markup.AdMarkupModel
import com.intellij.openapi.editor.impl.ad.util.ThreadLocalRhizomeDB
import com.intellij.platform.util.coroutines.childScope
import com.intellij.util.concurrency.AppExecutorUtil
import fleet.kernel.transactor
import fleet.util.UID
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus.Experimental


@Experimental
@Service(Level.APP)
class AdTheManager(private val appCoroutineScope: CoroutineScope) {

  companion object {
    @JvmStatic
    fun getInstance(): AdTheManager = service()

    internal val LOG = logger<AdTheManager>()
    internal val AD_DISPATCHER by lazy {
      AppExecutorUtil.createBoundedApplicationPoolExecutor("AD_DISPATCHER", 1).asCoroutineDispatcher()
    }
  }

  fun getEditorModel(editor: EditorImpl): EditorModel? {
    if (isEnabled()) {
      val document = editor.document
      val editorId = document.getUserData(KERNEL_EDITOR_ID_KEY)
      if (editorId != null) {
        val debugName = editor.toString()
        val editorUid = UID.fromString(editorId.serializeToString())
        val docMarkup = DocumentMarkupModel.forDocument(document, editor.project, true) as MarkupModelEx
        val editorMarkup = editor.markupModel
        runCatching {
          val docEntity = AdDocumentManager.getInstance().getDocEntityRunBlocking(document)
          val docMarkupEntity = AdDocumentMarkupManager.getInstance().getMarkupEntityRunBlocking(docMarkup)
          val editorMarkupEntity = AdEditorMarkupManager.getInstance().createEditorMarkupEntityRunBlocking(editorUid, editorMarkup)
          if (docEntity != null && docMarkupEntity != null && editorMarkupEntity != null) {
            ThreadLocalRhizomeDB.setThreadLocalDb(ThreadLocalRhizomeDB.lastKnownDb())
            return object : EditorModel {
              override fun getDocument(): DocumentEx = AdDocument(docEntity)
              override fun getEditorMarkupModel(): MarkupModelEx = AdMarkupModel("editor", editorMarkupEntity)
              override fun getDocumentMarkupModel(): MarkupModelEx = EditorFilteringMarkupModelEx(editor, AdMarkupModel("document", docMarkupEntity))
              override fun getHighlighter(): EditorHighlighter = editor.highlighter
              override fun getInlayModel(): InlayModelEx = editor.inlayModel
              override fun getFoldingModel(): FoldingModelInternal = editor.foldingModel
              override fun getSoftWrapModel(): SoftWrapModelEx = editor.softWrapModel
              override fun getCaretModel(): CaretModel = editor.caretModel
              override fun getSelectionModel(): SelectionModel = editor.selectionModel
              override fun getScrollingModel(): ScrollingModel = editor.scrollingModel
              override fun getFocusModel(): FocusModeModel = editor.focusModeModel
              override fun isAd(): Boolean = true
              override fun dispose() {
                AdEditorMarkupManager.getInstance().deleteEditorMarkupEntityRunBlocking(editorMarkupEntity)
              }
            }
          }
        }.onFailure {
          LOG.error("failed to create editor model $debugName", it)
        }
      }
    }
    return null
  }

  fun bindEditor(editor: Editor) {
    if (isEnabled()) {
      val cs = appCoroutineScope.childScope("editor repaint on doc entity change")
      val disposable = Disposable { cs.cancel() }
      EditorUtil.disposeWithEditor(editor, disposable)
      cs.launch(AD_DISPATCHER) {
        transactor().log.collect { // TODO: track only text changes
          withContext(Dispatchers.EDT) {
            if (!editor.isDisposed) {
              editor.component.repaint() // TODO: repaint partially
            }
          }
        }
      }
    }
  }

  private fun isEnabled(): Boolean {
    return isRhizomeAdEnabled
  }
}
