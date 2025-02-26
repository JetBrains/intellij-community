// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.ad

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.isRhizomeAdEnabled
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.*
import com.intellij.openapi.editor.ex.*
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.editor.highlighter.EditorHighlighter
import com.intellij.openapi.editor.impl.DocumentMarkupModel
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.impl.FocusModeModel
import com.intellij.openapi.editor.impl.ad.document.AdDocument
import com.intellij.openapi.editor.impl.ad.document.AdDocumentEntityManager
import com.intellij.openapi.editor.impl.ad.markup.AdMarkupModel
import com.intellij.openapi.editor.impl.ad.markup.AdMarkupModelManager
import com.intellij.openapi.editor.impl.ad.util.ThreadLocalRhizomeDB
import com.intellij.openapi.project.Project
import com.intellij.platform.pasta.common.DocumentEntity
import com.intellij.platform.util.coroutines.childScope
import com.intellij.util.concurrency.AppExecutorUtil
import fleet.kernel.transactor
import fleet.util.logging.logger
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

  fun bindMarkupEntity(project: Project?, document: Document) {
    if (isEnabled() && project != null && document is DocumentEx) {
      val markupModel = DocumentMarkupModel.forDocument(document, project, true) as MarkupModelEx
      AdMarkupModelManager.getInstance(project).bindMarkupModelEntity(markupModel)
    }
  }

  fun getEditorModel(editor: EditorImpl): EditorModel? {
    if (isEnabled()) {
      val docEntity = docEntity(editor)
      if (docEntity != null) {
        val project = editor.project!!
        val mm = DocumentMarkupModel.forDocument(editor.document, project, true) as MarkupModelEx
        val adMarkupModel = adMarkupModel(editor, mm)
        ThreadLocalRhizomeDB.setThreadLocalDb(ThreadLocalRhizomeDB.lastKnownDb())
        return object : EditorModel {
          override fun getDocument(): DocumentEx = AdDocument(docEntity)
          override fun getEditorMarkupModel(): MarkupModelEx = editor.markupModel
          override fun getDocumentMarkupModel(): MarkupModelEx = adMarkupModel // TODO: filtered
          override fun getHighlighter(): EditorHighlighter = editor.highlighter
          override fun getInlayModel(): InlayModelEx = editor.inlayModel
          override fun getFoldingModel(): FoldingModelEx = editor.foldingModel
          override fun getSoftWrapModel(): SoftWrapModelEx = editor.softWrapModel
          override fun getCaretModel(): CaretModel = editor.caretModel
          override fun getSelectionModel(): SelectionModel = editor.selectionModel
          override fun getScrollingModel(): ScrollingModel = editor.scrollingModel
          override fun getFocusModel(): FocusModeModel = editor.focusModeModel
          override fun isAd(): Boolean = true
          override fun dispose() = AdMarkupModelManager.getInstance(project).releaseMarkupModelEntity(mm)
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

  private fun docEntity(editor: Editor): DocumentEntity? {
    val document = editor.document
    val debugName = document.toString()
    return runCatching {
      AdDocumentEntityManager.getInstance().getDocEntityRunBlocking(document)
    }.onFailure {
      LOG.error(it) { "Failed to get doc entity $debugName" }
    }.getOrNull()
  }

  private fun adMarkupModel(editor: Editor, markupModel: MarkupModelEx): AdMarkupModel {
    val project = editor.project!!
    val manager = AdMarkupModelManager.getInstance(project)
    val entity = manager.getMarkupEntityRunBlocking(markupModel)!!
    return AdMarkupModel(entity)
  }

  private fun isEnabled(): Boolean {
    return isRhizomeAdEnabled
  }
}
