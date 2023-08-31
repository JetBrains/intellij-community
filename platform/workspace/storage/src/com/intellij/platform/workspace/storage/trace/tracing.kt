// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.trace

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.impl.*
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentationApi
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageSnapshotInstrumentation
import com.intellij.platform.workspace.storage.url.VirtualFileUrlIndex


/**
 * EntityStorageSnapshot tracer. The snapshot can be wrapped with this tracer
 *   and all reads of the original snapshot will produce a read trace that will be passed to [onRead] function
 */
@OptIn(EntityStorageInstrumentationApi::class)
internal class ReadTracker(
  internal val snapshot: EntityStorageSnapshotInstrumentation,
  private val onRead: (ReadTrace) -> Unit
) : EntityStorageSnapshotInstrumentation by snapshot {
  override fun <E : WorkspaceEntity> entities(entityClass: Class<E>): Sequence<E> {
    val trace = ReadTrace.EntitiesOfType(entityClass)
    log.trace { "Read trace of `entities` function: $trace" }
    onRead(trace)
    return snapshot.entities(entityClass)
      .onEach {
        // We have to pass this snapshot because the entity is created in internals of the original snapshot and there is no way to pass
        //  a different snapshot while creation. I hope I can get rid of this line.
        it.asBase().snapshot = this

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
      .onEach {
        // We have to pass this snapshot because the entity is created in internals of the original snapshot and there is no way to pass
        //  a different snapshot while creation. I hope I can get rid of this line.
        it.asBase().snapshot = this
      }
  }

  override fun <E : WorkspaceEntityWithSymbolicId> resolve(id: SymbolicEntityId<E>): E? {
    val trace = ReadTrace.Resolve(id)
    log.trace { "Read trace of `resolve` function: $trace" }
    onRead(trace)
    return snapshot.resolve(id)?.also {
      it.asBase().snapshot = this
    }
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

  override fun getOneChild(connectionId: ConnectionId, parent: WorkspaceEntity): WorkspaceEntity? {
    val trace = ReadTrace.SomeFieldAccess(parent.asBase().id)
    log.trace { "Read trace of `getOneChild` function: $trace" }
    onRead(trace)
    return snapshot.getOneChild(connectionId, parent)
  }

  override fun getManyChildren(connectionId: ConnectionId, parent: WorkspaceEntity): Sequence<WorkspaceEntity> {
    val trace = ReadTrace.SomeFieldAccess(parent.asBase().id)
    log.trace { "Read trace of `getManyChildren` function: $trace" }
    onRead(trace)
    return snapshot.getManyChildren(connectionId, parent)
  }

  override fun getParent(connectionId: ConnectionId, child: WorkspaceEntity): WorkspaceEntity? {
    val trace = ReadTrace.SomeFieldAccess(child.asBase().id)
    log.trace { "Read trace of `getParent` function: $trace" }
    onRead(trace)
    return snapshot.getParent(connectionId, child)
  }

  override fun <T : WorkspaceEntity> resolveReference(reference: EntityReference<T>): T? {
    val entity = snapshot.resolveReference(reference)
    entity?.asBase()?.snapshot = this
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

@Suppress("UNCHECKED_CAST")
internal fun Map<Class<*>, List<EntityChange<*>>>.toTraces(): Set<ReadTrace> {
  return buildSet {
    this@toTraces.forEach { (key, changes) ->
      changes.forEach { change ->
        when (change) {
          is EntityChange.Added -> {
            val entity = change.entity

            add(ReadTrace.EntitiesOfType(key as Class<out WorkspaceEntity>))

            val id = entity.asBase().id
            val entityData = entity.asBase().snapshot.abstract().entityDataByIdOrDie(id)
            if (entityData is SoftLinkable) {
              entityData.getLinks().forEach { link ->
                add(ReadTrace.HasSymbolicLinkTo(link, key))
              }
            }

            if (entity is WorkspaceEntityWithSymbolicId) {
              add(ReadTrace.Resolve(entity.symbolicId))
            }
          }
          is EntityChange.Removed -> {
            val entity = change.entity

            add(ReadTrace.EntitiesOfType(key as Class<out WorkspaceEntity>))

            val id = entity.asBase().id
            val entityData = entity.asBase().snapshot.abstract().entityDataByIdOrDie(id)
            if (entityData is SoftLinkable) {
              entityData.getLinks().forEach { link ->
                add(ReadTrace.HasSymbolicLinkTo(link, key))
              }
            }

            if (entity is WorkspaceEntityWithSymbolicId) {
              add(ReadTrace.Resolve(entity.symbolicId))
            }
          }
          is EntityChange.Replaced -> {
            add(ReadTrace.SomeFieldAccess((change.newEntity as WorkspaceEntityBase).id))

            // We generate this event to cause `entities` note to react on change of fields. However, this approach is questionable.
            add(ReadTrace.EntitiesOfType(key as Class<out WorkspaceEntity>))

            val entity = change.newEntity

            // Becase maybe we update the field with links
            val id = entity.asBase().id
            val entityData = entity.asBase().snapshot.abstract().entityDataByIdOrDie(id)
            if (entityData is SoftLinkable) {
              entityData.getLinks().forEach { link ->
                add(ReadTrace.HasSymbolicLinkTo(link, key))
              }
            }

            // Because maybe we update the field that calculates symbolic id
            if (entity is WorkspaceEntityWithSymbolicId) {
              add(ReadTrace.Resolve(entity.symbolicId))
            }
          }
        }
      }
    }
  }
}

@OptIn(EntityStorageInstrumentationApi::class)
private fun EntityStorage.abstract(): AbstractEntityStorage {
  if (this is ReadTracker) {
    return this.snapshot as AbstractEntityStorage
  }
  else {
    return this as AbstractEntityStorage
  }
}