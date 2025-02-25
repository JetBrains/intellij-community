// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.ad.document

import com.intellij.openapi.application.isRhizomeAdEnabled
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.ex.DocumentEx
import com.intellij.openapi.editor.impl.ad.AdTheManager
import com.intellij.openapi.fileEditor.impl.FileDocumentBindingListener
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.platform.pasta.common.DocumentEntity
import com.intellij.util.concurrency.ThreadingAssertions
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import org.jetbrains.annotations.ApiStatus.Experimental
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.withLock


@Experimental
@Service(Level.APP)
internal class AdDocumentEntityManager(private val coroutineScope: CoroutineScope) {

  companion object {
    fun getInstance(): AdDocumentEntityManager = service()
  }

  // guard DOC_ENTITY_HANDLE_KEY
  private val lock = java.util.concurrent.locks.ReentrantLock()

  suspend fun getDocEntity(document: Document): DocumentEntity? {
    return getDocHandle(document)?.entity()
  }

  fun getDocEntityRunBlocking(document: Document): DocumentEntity? {
    return getDocHandle(document)?.entityRunBlocking()
  }

  fun bindDocEntity(document: Document, oldFile: VirtualFile?, file: VirtualFile?) {
    if (isEnabled() &&
        document is DocumentEx &&
        oldFile == null && file != null /* TODO: listen not only this case */) {
      val provider = AdDocumentEntityProvider.getInstance()
      if (provider.canCreateEntity(file, document)) {
        lock.withLock { // ensure createEntity is called only once
          if (document.getUserData(DOC_ENTITY_HANDLE_KEY) == null) {
            val entityRef = AtomicReference<DocumentEntity>()
            val deferredRef = AtomicReference<Deferred<DocumentEntity>>(ENTITY_IS_NOT_READY)
            val debugName = document.toString()

            val entityDeferred = coroutineScope.async(AdTheManager.AD_DISPATCHER) { // TODO: can be lazy
              AdTheManager.LOG.debug {
                "binding doc entity $debugName with provider ${provider.javaClass.simpleName}"
              }
              val entity = provider.createEntity(file, document)
              entityRef.set(entity)
              deferredRef.set(ENTITY_IS_READY) // release document hard reference captured by this lambda
              entity
            }

            // entityDeferred may finish before this cas
            deferredRef.compareAndSet(ENTITY_IS_NOT_READY, entityDeferred)

            EntityCleanService.getInstance().registerEntity(document, debugName) {
              val entity = entityDeferred.await()
              provider.deleteEntity(entity)
            }

            document.putUserData(DOC_ENTITY_HANDLE_KEY, DocEntityHandle(entityRef, deferredRef, debugName))
          }
        }
      }
    }
  }

  private fun getDocHandle(document: Document): DocEntityHandle? {
    if (isEnabled() && document is DocumentEx) {
      return lock.withLock {
        document.getUserData(DOC_ENTITY_HANDLE_KEY)
      }
    }
    return null
  }

  private fun isEnabled(): Boolean {
    return isRhizomeAdEnabled
  }
}

private class MyFileDocumentBindingListener : FileDocumentBindingListener {
  override fun fileDocumentBindingChanged(document: Document, oldFile: VirtualFile?, file: VirtualFile?) {
    AdDocumentEntityManager.getInstance().bindDocEntity(document, oldFile, file)
  }
}

private val DOC_ENTITY_HANDLE_KEY: Key<DocEntityHandle> = Key("AD_DOC_ENTITY_KEY")
private val ENTITY_IS_READY: Deferred<DocumentEntity> = CompletableDeferred()
private val ENTITY_IS_NOT_READY: Deferred<DocumentEntity>? = null

private data class DocEntityHandle(
  private val entityRef: AtomicReference<DocumentEntity>,
  private val entityDeferredRef: AtomicReference<Deferred<DocumentEntity>>,
  private val debugName: String,
) {

  suspend fun entity(): DocumentEntity {
    val (deferred, entity) = entityPair()
    if (entity != null) {
      return entity
    }
    return deferred!!.await()
  }

  fun entityRunBlocking(): DocumentEntity {
    ThreadingAssertions.assertEventDispatchThread()
    val (deferred, entity) = entityPair()
    if (entity != null) {
      return entity
    }
    AdTheManager.LOG.debug { "slow path entity creation $debugName" }
    return runWithModalProgressBlocking(
      ModalTaskOwner.guess(),
      "Shared document entity creation $debugName",
    ) { deferred!!.await() }
  }

  private fun entityPair(): Pair<Deferred<DocumentEntity>?, DocumentEntity?> {
    val deferred = entityDeferredRef.get()
    val entity = entityRef.get()
    if (entity != null) { // fast path
      return (null to entity)
    }
    assert(deferred != ENTITY_IS_READY)
    assert(deferred != ENTITY_IS_NOT_READY)
    return (deferred to null)
  }

  override fun toString(): String {
    val entity = entityRef.get()
    return "DocEntityHandle($debugName, isReady=${entity != null}, ref=$entityRef, deferredRef=$entityDeferredRef)"
  }
}
