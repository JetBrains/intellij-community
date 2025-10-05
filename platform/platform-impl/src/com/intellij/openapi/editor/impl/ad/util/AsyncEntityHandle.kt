// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.ad.util

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.editor.impl.ad.AdTheManager
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.util.concurrency.ThreadingAssertions
import com.jetbrains.rhizomedb.Entity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import org.jetbrains.annotations.ApiStatus.Experimental
import java.util.concurrent.atomic.AtomicReference


@Experimental
@Service(Level.APP)
internal class AsyncEntityService(private val coroutineScope: CoroutineScope) {

  companion object {
    fun getInstance(): AsyncEntityService = service()
  }

  fun <E : Entity> createHandle(debugName: String, entityProvider: suspend () -> E): AsyncEntityHandle<E> {
    val entityRef = AtomicReference<Entity>()
    val deferredRef = AtomicReference<Deferred<Entity>>(ENTITY_IS_NOT_READY)

    val entityDeferred = coroutineScope.async(AdTheManager.AD_DISPATCHER) { // TODO: can be lazy
      AdTheManager.LOG.debug { "shared entity creation $debugName" }
      val entity = entityProvider()
      entityRef.set(entity)
      deferredRef.set(ENTITY_IS_READY) // release hard reference captured by this lambda
      entity
    }

    // entityDeferred may finish before this cas
    deferredRef.compareAndSet(ENTITY_IS_NOT_READY, entityDeferred)

    @Suppress("UNCHECKED_CAST") // ENTITY_IS_NOT_READY and ENTITY_IS_READY are never dereferenced
    return AsyncEntityHandle(
      debugName,
      entityRef as AtomicReference<E>,
      deferredRef as AtomicReference<Deferred<E>>,
    )
  }
}

private val ENTITY_IS_READY: Deferred<Entity> = CompletableDeferred()
private val ENTITY_IS_NOT_READY: Deferred<Entity>? = null

@Experimental
internal class AsyncEntityHandle<E : Entity>(
  private val debugName: String,
  private val entityRef: AtomicReference<E>,
  private val entityDeferredRef: AtomicReference<Deferred<E>>,
) {

  suspend fun entity(): E {
    val (deferred, entity) = entityPair()
    if (entity != null) {
      return entity
    }
    checkNotNull(deferred) { "impossible happened" }
    return deferred.await()
  }

  fun entityRunBlocking(): E {
    ThreadingAssertions.assertEventDispatchThread()
    val (deferred, entity) = entityPair()
    if (entity != null) {
      return entity
    }
    checkNotNull(deferred) { "impossible happened" }
    AdTheManager.LOG.debug { "slow path entity creation on EDT $debugName" }
    @Suppress("HardCodedStringLiteral") // TODO: message is used only for debug purpose
    return runWithModalProgressBlocking(
      ModalTaskOwner.guess(),
      "shared entity creation $debugName",
    ) { deferred.await() }
  }

  private fun entityPair(): Pair<Deferred<E>?, E?> {
    val deferred = entityDeferredRef.get()
    val entity = entityRef.get()
    if (entity != null) { // fast path
      return (null to entity)
    }
    check(deferred != ENTITY_IS_READY)
    check(deferred != ENTITY_IS_NOT_READY)
    return (deferred to null)
  }

  override fun toString(): String {
    val entity = entityRef.get()
    return "AsyncEntityHandle($debugName, isReady=${entity != null}, ref=$entityRef, deferredRef=$entityDeferredRef)"
  }
}
