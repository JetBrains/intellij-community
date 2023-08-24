// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.trace

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.impl.EntityId
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityBase
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentationApi
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageSnapshotInstrumentation
import com.intellij.platform.workspace.storage.url.VirtualFileUrlIndex


/**
 * EntityStorageSnapshot tracer. The snapshot can be wrapped with this tracer
 *   and all reads of the original snapshot will produce a read trace that will be passed to [onRead] function
 */
@OptIn(EntityStorageInstrumentationApi::class)
internal class ReadTracker(
  private val snapshot: EntityStorageSnapshotInstrumentation,
  private val onRead: (ReadTrace) -> Unit
) : EntityStorageSnapshotInstrumentation by snapshot {
  override fun <E : WorkspaceEntity> entities(entityClass: Class<E>): Sequence<E> {
    val trace = ReadTrace.EntitiesOfType(entityClass)
    log.trace { "Read trace of `entities` function: $trace" }
    onRead(trace)
    return snapshot.entities(entityClass)
      // TODO Get rid of this
      .onEach {
        (it as WorkspaceEntityBase).snapshot = this

        // TODO check if we need to keep this
        //(it as WorkspaceEntityBase).readTrace = onRead
      }
  }

  override fun <E : WorkspaceEntity> entitiesAmount(entityClass: Class<E>): Int {
    val trace = ReadTrace.EntitiesOfType(entityClass)
    log.trace { "Read trace of `entitiesAmount` function: $trace" }
    onRead(trace)
    return snapshot.entitiesAmount(entityClass)
  }

  override fun <E : WorkspaceEntityWithSymbolicId, R : WorkspaceEntity> referrers(id: SymbolicEntityId<E>,
                                                                                  entityClass: Class<R>): Sequence<R> {
    val trace = ReadTrace.HasSymbolicLinkTo(id, entityClass)
    log.trace { "Read trace of `referrers` function: $trace" }
    onRead(trace)
    return snapshot.referrers(id, entityClass)
  }

  override fun <E : WorkspaceEntityWithSymbolicId> resolve(id: SymbolicEntityId<E>): E? {
    val trace = ReadTrace.Resolve(id)
    log.trace { "Read trace of `resolve` function: $trace" }
    onRead(trace)
    return snapshot.resolve(id)
  }

  override fun <E : WorkspaceEntityWithSymbolicId> contains(id: SymbolicEntityId<E>): Boolean {
    val trace = ReadTrace.Resolve(id)
    log.trace { "Read trace of `contains` function: $trace" }
    onRead(trace)
    return snapshot.contains(id)
  }

  override fun <T> getExternalMapping(identifier: String): ExternalEntityMapping<T> {
    TODO("The external mapping is not supported for read tracing at the moment")
  }

  override fun getVirtualFileUrlIndex(): VirtualFileUrlIndex {
    TODO("The VirtualFileUrlIndex is not supported for read tracing at the moment")
  }

  override fun entitiesBySource(sourceFilter: (EntitySource) -> Boolean): Map<EntitySource, Map<Class<out WorkspaceEntity>, List<WorkspaceEntity>>> {
    TODO("The entitiesBySource is not supported for read tracing at the moment")
  }

  override fun <T : WorkspaceEntity> initializeEntity(entityId: EntityId, newInstance: () -> T): T {
    val newEntity = newInstance()
    newEntity as WorkspaceEntityBase
    newEntity.snapshot = this
    return newEntity
  }

  override fun <T : WorkspaceEntity> resolveReference(reference: EntityReference<T>): T? {
    val entity = snapshot.resolveReference(reference)
    if (entity != null) {
      entity as WorkspaceEntityBase
      entity.snapshot = this

      // TODO Check if we need to get this back
      //entity.readTrace = this.onRead
    }
    return entity
  }

  companion object {
    private val log = logger<ReadTracker>()
  }
}

internal sealed interface ReadTrace {
  data class EntitiesOfType(val ofClass: Class<out WorkspaceEntity>) : ReadTrace
  data class HasSymbolicLinkTo(
    val linkTo: SymbolicEntityId<WorkspaceEntityWithSymbolicId>,
    val inClass: Class<out WorkspaceEntity>,
  ) : ReadTrace

  data class Resolve(val link: SymbolicEntityId<WorkspaceEntityWithSymbolicId>) : ReadTrace

  data class FieldAccess(
    internal val entityId: EntityId,
    val fieldName: String,
  ) : ReadTrace

  data class SomeFieldAccess(
    internal val entityId: EntityId,
  ) : ReadTrace
}

internal fun Map<Class<*>, List<EntityChange<*>>>.toTraces(): Set<ReadTrace> {
  return buildSet {
    this@toTraces.forEach { (key, changes) ->
      if (changes.any { it is EntityChange.Added }) {
        add(ReadTrace.EntitiesOfType(key as Class<out WorkspaceEntity>))
      }
      if (changes.any { it is EntityChange.Removed }) {
        add(ReadTrace.EntitiesOfType(key as Class<out WorkspaceEntity>))
      }
      changes.forEach { change ->
        when (change) {
          is EntityChange.Added -> Unit
          is EntityChange.Removed -> Unit
          is EntityChange.Replaced -> add(ReadTrace.SomeFieldAccess((change.newEntity as WorkspaceEntityBase).id))
        }
      }
    }
  }
}