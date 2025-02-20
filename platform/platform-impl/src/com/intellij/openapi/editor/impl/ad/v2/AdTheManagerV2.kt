// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.ad.v2

import andel.intervals.AnchorStorage
import andel.operation.Operation
import andel.text.Text
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.isRhizomeAdEnabled
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.ex.DocumentEx
import com.intellij.openapi.editor.ex.PrioritizedDocumentListener
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.impl.ad.ThreadLocalRhizomeDB
import com.intellij.platform.pasta.common.DocumentEntity
import com.intellij.platform.pasta.common.DocumentEntity.Companion.EditLogAttr
import com.intellij.platform.pasta.common.DocumentEntity.Companion.SharedAnchorStorageAttr
import com.intellij.platform.pasta.common.DocumentEntity.Companion.TextAttr
import com.intellij.platform.pasta.common.DocumentEntity.Companion.WritableAttr
import com.intellij.platform.pasta.common.createEmptyEditLog
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.util.coroutines.childScope
import com.intellij.util.asDisposable
import com.intellij.util.concurrency.AppExecutorUtil
import fleet.kernel.Durable
import fleet.kernel.awaitCommitted
import fleet.kernel.change
import fleet.kernel.rete.each
import fleet.kernel.rete.filter
import fleet.kernel.rete.first
import fleet.kernel.shared
import fleet.kernel.transactor
import fleet.util.UID
import fleet.util.openmap.OpenMap
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus.Experimental
import java.util.*
import java.util.concurrent.atomic.AtomicReference

private val AD_DISPATCHER by lazy {
  AppExecutorUtil.createBoundedApplicationPoolExecutor("AD_DISPATCHER_V2", 1).asCoroutineDispatcher()
}

@Experimental
@Service(Level.APP)
class AdTheManagerV2(private val coroutineScope: CoroutineScope) {

  companion object {
    @JvmStatic
    fun getInstance(): AdTheManagerV2 = service()
  }

  private val docToEntityMap = IdentityHashMap<DocumentEx, DocumentEntityHandle>()
  private val docToSynchronizerMap = IdentityHashMap<DocumentEx, DocumentListener>()

  fun bindBackendDocEntity(file: VirtualFile, lazyDocumentId: (document: DocumentEx) -> Any) {
    if (isEnabled()) {
      val document = FileDocumentManager.getInstance().getDocument(file)
      if (document is DocumentEx) {
        synchronized(docToEntityMap) {
          val entityHandle = docToEntityMap[document]
          val nextEntityHandle = if (entityHandle != null) {
            entityHandle.inc()
          } else {
            val documentId = lazyDocumentId(document)
            val uid = hackyDocumentId(documentId)
            val entityRef = AtomicReference<DocumentEntity>()
            val entityDeferred = coroutineScope.async(AD_DISPATCHER) {
              val entity = change {
                shared {
                  DocumentEntity.new {
                    it[Durable.Id] = uid
                    it[TextAttr] = Text.fromString(document.immutableCharSequence.toString())
                    it[WritableAttr] = true
                    it[EditLogAttr] = createEmptyEditLog()
                    it[SharedAnchorStorageAttr] = AnchorStorage.empty()
                  }
                }
              }
              entityRef.set(entity)
              entity
            }
            val createdEntityHandle = DocumentEntityHandle(documentId, uid, entityDeferred, entityRef, refCount = 1)
            val synchronizer = DocumentToEntitySynchronizer(createdEntityHandle, coroutineScope)
            val existing = docToSynchronizerMap.put(document, synchronizer)
            assert(existing == null) { "doc synchronizer must be absent" }
            document.addDocumentListener(synchronizer, coroutineScope.asDisposable())
            createdEntityHandle
          }
          docToEntityMap[document] = nextEntityHandle
        }
      }
    }
  }

  fun bindFrontendDocEntity(documentId: Any, document: Document?) {
    if (isEnabled() && document is DocumentEx) {
      val uid = hackyDocumentId(documentId)
      synchronized(docToEntityMap) {
        val entityRef = AtomicReference<DocumentEntity>()
        val entityDeferred: Deferred<DocumentEntity> = coroutineScope.async(AD_DISPATCHER) {
          val entity = DocumentEntity.each().filter { it.uid == uid }.first()
          entityRef.set(entity)
          entity
        }
        docToEntityMap[document] = DocumentEntityHandle(documentId, uid, entityDeferred, entityRef, refCount = Int.MAX_VALUE)
      }
    }
  }

  fun releaseDocEntity(document: DocumentEx) {
    if (isEnabled()) {
      synchronized(docToEntityMap) {
        val entityHandle = docToEntityMap[document]
        checkNotNull(entityHandle) { "doc entity not found" }
        val nextEntityHandle = entityHandle.dec()
        if (nextEntityHandle == null) {
          val synchronizer = docToSynchronizerMap.remove(document)
          if (synchronizer != null) { // backend branch
            document.removeDocumentListener(synchronizer)
            coroutineScope.launch(AD_DISPATCHER) {
              val entity = entityHandle.entity()
              change {
                shared {
                  entity.delete()
                }
              }
            }
          }
        }
        docToEntityMap[document] = nextEntityHandle
      }
    }
  }

  fun getAdDocument(document: DocumentEx): DocumentEx? {
    if (isEnabled()) {
      val entity = synchronized(docToEntityMap) {
        docToEntityMap[document]
      }?.entity0()
      if (entity != null) {
        ThreadLocalRhizomeDB.setThreadLocalDb(ThreadLocalRhizomeDB.lastKnownDb())
        return AdDocumentV2(entity)
      }
    }
    return null
  }

  fun bindEditor(editor: EditorImpl) {
    if (isEnabled()) {
      val cs = coroutineScope.childScope("editor repaint on doc entity change")
      val disposable = Disposable { cs.cancel() }
      EditorUtil.disposeWithEditor(editor, disposable)
      cs.launch(AD_DISPATCHER) {
        transactor().log.collect {
          // TODO: track only text changes
          withContext(Dispatchers.EDT) {
            editor.repaintToScreenBottom(0)
          }
        }
      }
    }
  }

  private fun hackyDocumentId(documentId: Any): UID {
    val bytes = documentId.toString().toByteArray()
    val uuid = UUID.nameUUIDFromBytes(bytes)
    return UID.fromString(uuid.toString())
  }

  private fun isEnabled(): Boolean {
    return isRhizomeAdEnabled && Registry.`is`("ijpl.rhizome.ad.v2.enabled", false)
  }
}

private data class DocumentEntityHandle(
  private val documentId: Any,
  private val entityId: UID,
  private val entityDeferred: Deferred<DocumentEntity>,
  private val entityRef: AtomicReference<DocumentEntity>,
  private val refCount: Int,
) {
  fun inc(): DocumentEntityHandle = copy(refCount = refCount + 1)
  fun dec(): DocumentEntityHandle? = if (refCount > 1) copy(refCount = refCount - 1) else null
  suspend fun entity(): DocumentEntity = entityDeferred.await()
  fun entity0(): DocumentEntity = entityRef.get() ?: runBlocking { entity() }
}

private class DocumentToEntitySynchronizer(
  private val entityHandle: DocumentEntityHandle,
  private val coroutineScope: CoroutineScope,
) : PrioritizedDocumentListener {

  override fun getPriority(): Int = Int.MIN_VALUE + 1

  override fun documentChanged(event: DocumentEvent) {
    val operation = operation(event)
    val deferred = coroutineScope.async(AD_DISPATCHER) {
      val entity = entityHandle.entity()
      change { // it is a shared change
        entity.mutate(this, OpenMap.empty()) {
          edit(operation)
        }
      }
      awaitCommitted()
    }
    runBlocking { deferred.await() }
    ThreadLocalRhizomeDB.setThreadLocalDb(ThreadLocalRhizomeDB.lastKnownDb())
  }

  private fun operation(event: DocumentEvent): Operation {
    val oldText = event.oldFragment.toString()
    val newText = event.newFragment.toString()
    val lengthBefore = event.document.textLength - newText.length + oldText.length
    return Operation.Companion.replaceAt(
      offset = event.offset.toLong(),
      oldText = oldText,
      newText = newText,
      totalLength = lengthBefore.toLong(),
    )
  }
}
