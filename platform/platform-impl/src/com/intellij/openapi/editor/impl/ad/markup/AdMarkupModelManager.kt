// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.ad.markup

import com.intellij.openapi.application.isRhizomeAdEnabled
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.ex.MarkupModelEx
import com.intellij.openapi.editor.impl.ad.AdTheManager
import com.intellij.openapi.editor.impl.ad.document.AdEntityProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.util.concurrency.ThreadingAssertions
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import org.jetbrains.annotations.ApiStatus.Experimental
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.withLock


@Experimental
@Service(Level.PROJECT)
internal class AdMarkupModelManager(private val project: Project, private val coroutineScope: CoroutineScope) {

  companion object {
    fun getInstance(project: Project): AdMarkupModelManager = project.service()
  }

  private val lock = java.util.concurrent.locks.ReentrantLock()

  suspend fun getMarkupEntity(markupModel: MarkupModelEx): AdMarkupEntity? {
    return getMarkupHandle(markupModel)?.entity()
  }

  fun getMarkupEntityRunBlocking(markupModel: MarkupModelEx): AdMarkupEntity? {
    return getMarkupHandle(markupModel)?.entityRunBlocking()
  }

  fun bindMarkupModelEntity(markupModel: MarkupModelEx) {
    if (isEnabled()) {
      val provider = AdEntityProvider.getInstance()
      val uid = provider.getMarkupEntityUid(project, markupModel)
      if (uid != null) {
        lock.withLock {
          val existingHandle = markupModel.getUserData(MARKUP_ENTITY_HANDLE_KEY)
          val newHandle = if (existingHandle != null) {
            existingHandle.incRef()
          } else {
            val entityRef = AtomicReference<AdMarkupEntity>()
            val deferredRef = AtomicReference<Deferred<AdMarkupEntity>>(ENTITY_IS_NOT_READY)
            val debugName = markupModel.document.toString()

            val entityDeferred = coroutineScope.async(AdTheManager.AD_DISPATCHER) { // TODO: can be lazy
              AdTheManager.LOG.debug {
                "binding markup entity $debugName with provider ${provider.javaClass.simpleName}"
              }
              val entity = provider.createMarkupEntity(uid, project, markupModel)
              entityRef.set(entity)
              deferredRef.set(ENTITY_IS_READY) // release document hard reference captured by this lambda
              entity
            }

            // entityDeferred may finish before this cas
            deferredRef.compareAndSet(ENTITY_IS_NOT_READY, entityDeferred)

            MarkupEntityHandle(debugName, entityRef, deferredRef, refCount = 1)
          }

          markupModel.putUserData(MARKUP_ENTITY_HANDLE_KEY, newHandle)
        }
      }
    }
  }

  fun releaseMarkupModelEntity(markupModel: MarkupModelEx) {
    if (isEnabled()) {
      val provider = AdEntityProvider.getInstance()
      lock.withLock {
        val existingHandle = markupModel.getUserData(MARKUP_ENTITY_HANDLE_KEY)
        if (existingHandle == null) {
          throw IllegalStateException("handle not found")
        }
        val newHandle = existingHandle.decRef()
        markupModel.putUserData(MARKUP_ENTITY_HANDLE_KEY, newHandle)
        if (newHandle == null) {
          coroutineScope.async(AdTheManager.AD_DISPATCHER) {
            val entity = existingHandle.entity()
            provider.deleteMarkupEntity(entity)
          }
        }
      }
    }
  }

  private fun getMarkupHandle(markupModel: MarkupModelEx): MarkupEntityHandle? {
    if (isEnabled()) {
      return lock.withLock {
        markupModel.getUserData(MARKUP_ENTITY_HANDLE_KEY)
      }
    }
    return null
  }

  private fun isEnabled(): Boolean {
    return isRhizomeAdEnabled
  }
}

private val MARKUP_ENTITY_HANDLE_KEY: Key<MarkupEntityHandle> = Key.create("AD_MARKUP_ENTITY_HANDLE_KEY")
private val ENTITY_IS_READY: Deferred<AdMarkupEntity> = CompletableDeferred()
private val ENTITY_IS_NOT_READY: Deferred<AdMarkupEntity>? = null

private data class MarkupEntityHandle(
  private val debugName: String,
  private val entityRef: AtomicReference<AdMarkupEntity>,
  private val entityDeferredRef: AtomicReference<Deferred<AdMarkupEntity>>,
  private val refCount: Int,
) {

  fun incRef(): MarkupEntityHandle {
    assert(refCount > 0)
    return copy(refCount = refCount + 1)
  }

  fun decRef(): MarkupEntityHandle? {
    assert(refCount > 0)
    return if (refCount > 1) copy(refCount = refCount - 1) else null
  }

  suspend fun entity(): AdMarkupEntity {
    val (deferred, entity) = entityPair()
    if (entity != null) {
      return entity
    }
    return deferred!!.await()
  }

  fun entityRunBlocking(): AdMarkupEntity {
    ThreadingAssertions.assertEventDispatchThread()
    val (deferred, entity) = entityPair()
    if (entity != null) {
      return entity
    }
    AdTheManager.LOG.debug { "slow path entity creation $debugName" }
    return runWithModalProgressBlocking(
      ModalTaskOwner.guess(),
      "Shared entity creation $debugName",
    ) { deferred!!.await() }
  }

  private fun entityPair(): Pair<Deferred<AdMarkupEntity>?, AdMarkupEntity?> {
    val deferred = entityDeferredRef.get()
    val entity = entityRef.get()
    if (entity != null) { // fast path
      return (null to entity)
    }
    assert(deferred != ENTITY_IS_READY)
    assert(deferred != ENTITY_IS_NOT_READY)
    return (deferred to null)
  }
}
