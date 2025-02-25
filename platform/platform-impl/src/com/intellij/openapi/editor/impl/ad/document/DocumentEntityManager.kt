// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.ad.document

import com.intellij.openapi.application.isRhizomeAdEnabled
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.ex.DocumentEx
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.editor.impl.ad.AD_DISPATCHER
import com.intellij.openapi.fileEditor.impl.FileDocumentBindingListener
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.pasta.common.DocumentEntity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.jetbrains.annotations.ApiStatus.Experimental
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.withLock


@Experimental
@Service(Level.APP)
internal class DocumentEntityManager(private val coroutineScope: CoroutineScope) {

  companion object {
    fun getInstance(): DocumentEntityManager = service()
  }

  // guard DOC_ENTITY_HANDLE_KEY
  private val lock = java.util.concurrent.locks.ReentrantLock()

  suspend fun getDocEntity(document: Document): DocumentEntity? {
    if (isEnabled() && document is DocumentEx) {
      val handle = lock.withLock {
        document.getUserData(DOC_ENTITY_HANDLE_KEY)
      }
      return handle?.entity()
    }
    return null
  }

  fun getDocEntityRunBlocking(document: Document): DocumentEntity? {
    if (isEnabled() && document is DocumentEx) {
      val handle = lock.withLock {
        document.getUserData(DOC_ENTITY_HANDLE_KEY)
      }
      return runBlocking { handle?.entity() }
    }
    return null
  }

  internal fun bindDocEntity(document: Document, oldFile: VirtualFile?, file: VirtualFile?) {
    if (isEnabled() &&
        document is DocumentEx &&
        (document is DocumentImpl) && document.isWriteThreadOnly &&
        oldFile == null && file != null /* TODO: listen not only this case */) {
      val provider = DocumentEntityProvider.getInstance()
      if (provider.canCreateEntity(file, document)) {
        lock.withLock { // ensure createEntity is called only once
          if (document.getUserData(DOC_ENTITY_HANDLE_KEY) == null) {
            println("binding doc<->entity $document $oldFile $file")

            val entityRef = AtomicReference<DocumentEntity>()
            val deferredRef = AtomicReference<Deferred<DocumentEntity>>(ENTITY_IS_NOT_READY)

            val entityDeferred = coroutineScope.async(AD_DISPATCHER) {
              val entity = provider.createEntity(file, document)
              entityRef.set(entity)
              deferredRef.set(ENTITY_IS_READY) // release document hard reference captured by this lambda
              entity
            }

            // entityDeferred may finish before this cas
            deferredRef.compareAndSet(ENTITY_IS_NOT_READY, entityDeferred)

            EntityCleanService.getInstance().registerEntity(document, document.toString()) {
              val entity = entityDeferred.await()
              provider.deleteEntity(entity)
            }

            document.putUserData(DOC_ENTITY_HANDLE_KEY, DocEntityHandle(entityRef, deferredRef))
          }
        }
      }
    }
  }

  private fun isEnabled(): Boolean {
    return isRhizomeAdEnabled
  }
}

private class MyFileDocumentBindingListener : FileDocumentBindingListener {
  override fun fileDocumentBindingChanged(document: Document, oldFile: VirtualFile?, file: VirtualFile?) {
    DocumentEntityManager.getInstance().bindDocEntity(document, oldFile, file)
  }
}

private val DOC_ENTITY_HANDLE_KEY: Key<DocEntityHandle> = Key("AD_DOC_ENTITY_KEY")
private val ENTITY_IS_READY: Deferred<DocumentEntity> = CompletableDeferred()
private val ENTITY_IS_NOT_READY: Deferred<DocumentEntity>? = null

private data class DocEntityHandle(
  private val entityRef: AtomicReference<DocumentEntity>,
  private val entityDeferredRef: AtomicReference<Deferred<DocumentEntity>>,
) {
  suspend fun entity(): DocumentEntity {
    val deferredRef = entityDeferredRef.get()
    val entity = entityRef.get()
    if (entity != null) { // fast path
      return entity
    }
    assert(deferredRef != ENTITY_IS_READY)
    return deferredRef.await()
  }

  override fun toString(): String {
    val entity = entityRef.get()
    return "DocEntityHandle(isReady=${entity != null}, ref=$entityRef, deferredRef=$entityDeferredRef)"
  }
}
