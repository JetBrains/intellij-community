// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.ad

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.isRhizomeAdEnabled
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.DocumentEx
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.platform.pasta.common.DocumentEntity
import com.intellij.platform.util.coroutines.childScope
import com.intellij.util.awaitCancellationAndInvoke
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.ui.EDT
import fleet.kernel.change
import fleet.kernel.rete.each
import fleet.kernel.rete.filter
import fleet.kernel.rete.first
import fleet.kernel.shared
import fleet.kernel.transactor
import fleet.util.UID
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus.Experimental
import java.util.*
import java.util.concurrent.atomic.AtomicReference

private val AD_DISPATCHER by lazy {
  AppExecutorUtil.createBoundedApplicationPoolExecutor("AD_DISPATCHER_V2", 1).asCoroutineDispatcher()
}

@Experimental
@Service(Level.APP)
class AdTheManager(private val coroutineScope: CoroutineScope) {

  companion object {
    @JvmStatic
    fun getInstance(): AdTheManager = service()
  }

  private val docToHandle = IdentityHashMap<DocumentEx, DocumentEntityHandle>()

  fun bindBackendDocEntity(file: VirtualFile, lazyDocumentId: (document: DocumentEx) -> Any) {
    if (isEnabled()) {
      val document = FileDocumentManager.getInstance().getDocument(file)
      if (document is DocumentEx) {
        synchronized(docToHandle) {
          removeLocalDocEntity(document)
          bindBackendDocEntity(document, BindType.BACKEND, lazyDocumentId)
        }
      }
    }
  }

  fun bindFrontendDocEntity(documentId: Any, document: Document?) {
    if (isEnabled() && document is DocumentEx) {
      synchronized(docToHandle) {
        removeLocalDocEntity(document)
        bindDocEntity(
          document,
          BindType.FRONTEND,
          { documentId },
          createEntity = { entityId -> DocumentEntity.each().filter { it.uid == entityId }.first() },
          deleteEntity = {},
        )
      }
    }
  }

  fun bindLocalDocEntity(document: Document) {
    if (isEnabled() && document is DocumentEx) {
      synchronized(docToHandle) {
        val handle = docToHandle[document]
        if (handle == null || handle.isLocal()) {
          bindBackendDocEntity(document, BindType.LOCAL) { UUID.randomUUID() }
        }
      }
    }
  }

  fun releaseDocEntity(document: DocumentEx) {
    if (isEnabled()) {
      synchronized(docToHandle) {
        val handle = docToHandle[document]
        checkNotNull(handle) { "doc entity not found" }
        val nextHandle = handle.decRef()
        if (nextHandle != null) {
          docToHandle[document] = nextHandle
        } else {
          handle.dispose()
          docToHandle.remove(document)
        }
      }
    }
  }

  fun getAdDocument(document: DocumentEx): DocumentEx? {
    if (isEnabled()) {
      val entity = synchronized(docToHandle) {
        docToHandle[document]
      }?.entity()
      if (entity != null) {
        ThreadLocalRhizomeDB.setThreadLocalDb(ThreadLocalRhizomeDB.lastKnownDb())
        return AdDocument(entity)
      }
    }
    return null
  }

  fun bindEditor(editor: Editor) {
    if (isEnabled()) {
      val cs = coroutineScope.childScope("editor repaint on doc entity change")
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

  private fun bindBackendDocEntity(
    document: DocumentEx,
    bindType: BindType,
    lazyDocumentId: (document: DocumentEx) -> Any,
  ) {
    assert(isEnabled())
    assert(bindType != BindType.FRONTEND)
    val text = document.immutableCharSequence
    bindDocEntity(
      document,
      bindType,
      lazyDocumentId,
      createEntity = { entityId ->
        change {
          shared {
            DocumentEntity.fromText(entityId, text.toString())
          }
        }
      },
      deleteEntity = { entity ->
        change {
          shared {
            entity.delete()
          }
        }
      },
    )
  }

  private fun bindDocEntity(
    document: DocumentEx,
    bindType: BindType,
    lazyDocumentId: (document: DocumentEx) -> Any,
    createEntity: suspend (UID) -> DocumentEntity,
    deleteEntity: suspend (DocumentEntity) -> Unit,
  ) {
    assert(isEnabled())
    if (!EDT.isCurrentThreadEdt()) {
      ThreadingAssertions.assertReadAccess()
    }
    synchronized(docToHandle) {
      val handle = docToHandle[document]
      docToHandle[document] = if (handle != null) {
        handle.incRef()
      } else {
        val documentId = lazyDocumentId(document)
        val entityId = hackyEntityId(documentId)
        val cs = coroutineScope.childScope("docEntityScope($entityId)", AD_DISPATCHER)
        val entityRef = AtomicReference<DocumentEntity>()
        val entityDeferred = cs.async {
          val entity = createEntity(entityId)
          entityRef.set(entity)
          entity
        }
        if (bindType != BindType.FRONTEND) {
          val documentListener = AdDocumentSynchronizer(documentId, entityId, cs, entityDeferred)
          document.addDocumentListener(documentListener)
          @Suppress("OPT_IN_USAGE")
          cs.awaitCancellationAndInvoke {
            document.removeDocumentListener(documentListener)
            val entity = entityDeferred.await()
            deleteEntity(entity)
          }
        }
        DocumentEntityHandle(
          documentId,
          entityId,
          bindType,
          1,
          cs,
          entityDeferred,
          entityRef,
        )
      }
    }
  }

  private fun removeLocalDocEntity(document: DocumentEx) {
    val handle = docToHandle[document]
    if (handle != null && handle.isLocal()) {
      handle.dispose()
      docToHandle.remove(document)
    }
  }

  private fun hackyEntityId(documentId: Any): UID {
    val bytes = documentId.toString().toByteArray()
    val uuid = UUID.nameUUIDFromBytes(bytes)
    return UID.fromString(uuid.toString())
  }

  private fun isEnabled(): Boolean {
    return isRhizomeAdEnabled
  }
}

private enum class BindType {
  LOCAL, FRONTEND, BACKEND
}

private data class DocumentEntityHandle(
  private val documentId: Any, // RdDocumentId
  private val entityId: UID,
  private val bindType: BindType,
  private val refCount: Int,
  private val coroutineScope: CoroutineScope,
  private val entityDeferred: Deferred<DocumentEntity>,
  private val entityRef: AtomicReference<DocumentEntity>,
) {

  fun incRef(): DocumentEntityHandle {
    assert(refCount > 0)
    return copy(refCount = refCount + 1)
  }

  fun decRef(): DocumentEntityHandle? {
    assert(refCount > 0)
    return if (refCount > 1) copy(refCount = refCount - 1) else null
  }

  fun entity(): DocumentEntity {
    val entity = entityRef.get()
    if (entity != null) {
      return entity
    }
    @Suppress("HardCodedStringLiteral")
    return runWithModalProgressBlocking(
      ModalTaskOwner.guess(),
      "Shared document entity creation $entityId",
    ) { entityDeferred.await() }
  }

  fun isLocal(): Boolean {
    return bindType == BindType.LOCAL
  }

  fun dispose() {
    coroutineScope.cancel()
  }
}
