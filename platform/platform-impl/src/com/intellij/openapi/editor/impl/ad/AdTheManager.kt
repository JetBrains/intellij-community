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
import com.intellij.openapi.editor.impl.ad.document.DocumentEntityManager
import com.intellij.openapi.editor.impl.ad.markup.AdMarkupEntity
import com.intellij.openapi.editor.impl.ad.markup.AdMarkupModel
import com.intellij.openapi.editor.impl.ad.markup.AdMarkupSynchronizer
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.pasta.common.DocumentComponentEntity
import com.intellij.platform.pasta.common.DocumentEntity
import com.intellij.platform.util.coroutines.childScope
import com.intellij.util.concurrency.AppExecutorUtil
import com.jetbrains.rhizomedb.entities
import fleet.kernel.change
import fleet.kernel.shared
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

    private val MARKUP_SYNC_KEY: Key<AdMarkupSynchronizer> = Key.create("AD_MARKUP_SYNC_KEY")
    internal val LOG = logger<AdTheManager>()
    internal val AD_DISPATCHER by lazy {
      AppExecutorUtil.createBoundedApplicationPoolExecutor("AD_DISPATCHER", 1).asCoroutineDispatcher()
    }
  }

  // TODO: Only monolith mode so far. EditorId needed for split mode
  fun bindMarkupEntity(project: Project?, document: Document) {
    if (isEnabled() && isSharedMarkupEnabled() && project != null && document is DocumentEx) {
      val documentMarkup = DocumentMarkupModel.forDocument(document, project, true) as MarkupModelEx
      if (documentMarkup.getUserData(MARKUP_SYNC_KEY) == null) {
        val cs = appCoroutineScope.childScope("AdMarkupSynchronizer", AD_DISPATCHER)
        val async = cs.async {
          val docEntity = DocumentEntityManager.getInstance().getDocEntity(document)
          if (docEntity != null) {
            change {
              shared {
                AdMarkupEntity.empty(docEntity)
              }
            }
          } else {
            null
          }
        }
        val markupEntity = runBlocking { async.await() }
        if (markupEntity != null) {
          val markupSynchronizer = AdMarkupSynchronizer(markupEntity, documentMarkup, cs)

          // TODO: dispose with editor, remove MARKUP_SYNC_KEY
          val disposable = Disposer.newDisposable(project)

          document.addDocumentListener(markupSynchronizer, disposable)
          documentMarkup.addMarkupModelListener(disposable, markupSynchronizer)
        }
      }
    }
  }

  fun getEditorModel(editor: EditorImpl): EditorModel? {
    if (isEnabled()) {
      val docEntity = docEntity(editor)
      if (docEntity != null) {
        ThreadLocalRhizomeDB.setThreadLocalDb(ThreadLocalRhizomeDB.lastKnownDb())
        val adMarkupModel = adMarkupModel(docEntity)
        return object : EditorModel {
          override fun getDocument(): DocumentEx = AdDocument(docEntity)
          override fun getEditorMarkupModel(): MarkupModelEx = editor.markupModel
          override fun getDocumentMarkupModel(): MarkupModelEx = adMarkupModel ?: editor.filteredDocumentMarkupModel
          override fun getHighlighter(): EditorHighlighter = editor.highlighter
          override fun getInlayModel(): InlayModelEx = editor.inlayModel
          override fun getFoldingModel(): FoldingModelEx = editor.foldingModel
          override fun getSoftWrapModel(): SoftWrapModelEx = editor.softWrapModel
          override fun getCaretModel(): CaretModel = editor.caretModel
          override fun getSelectionModel(): SelectionModel = editor.selectionModel
          override fun getScrollingModel(): ScrollingModel = editor.scrollingModel
          override fun getFocusModel(): FocusModeModel = editor.focusModeModel
          override fun isAd(): Boolean = true
          override fun dispose() = releaseEditor()
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

  private fun releaseEditor() {
    // TODO
  }

  private fun docEntity(editor: Editor): DocumentEntity? {
    val document = editor.document
    val debugName = document.toString()
    return runCatching {
      DocumentEntityManager.getInstance().getDocEntityRunBlocking(document)
    }.onFailure {
      LOG.error(it) { "Failed to get doc entity $debugName" }
    }.getOrNull()
  }

  private fun adMarkupModel(docEntity: DocumentEntity): AdMarkupModel? {
    if (isSharedMarkupEnabled()) {
      val markupEntity = entities(DocumentComponentEntity.DocumentAttr, docEntity)
        .filterIsInstance<AdMarkupEntity>()
        .first() // single()  TODO
      return AdMarkupModel(markupEntity)
    }
    return null
  }

  private fun isSharedMarkupEnabled(): Boolean {
    return Registry.`is`("ijpl.rhizome.ad.markup.enabled", false)
  }

  private fun isEnabled(): Boolean {
    return isRhizomeAdEnabled
  }
}
