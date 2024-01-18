// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.platform.workspace.storage.trace

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.impl.*
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentationApi
import com.intellij.platform.workspace.storage.instrumentation.ImmutableEntityStorageInstrumentation
import com.intellij.platform.workspace.storage.url.VirtualFileUrlIndex
import it.unimi.dsi.fastutil.longs.LongArrayList
import it.unimi.dsi.fastutil.longs.LongOpenHashSet


/**
 * EntityStorageSnapshot tracer. The snapshot can be wrapped with this tracer
 *   and all reads of the original snapshot will produce a read trace that will be passed to [onRead] function
 *
 * Implementation note: There could be two options for attaching the tracker. One is an inheritance, the second is delegation. Fleet uses
 *   delegation, we use inheritance.
 * The decision to use inheritance comes from how we create entities. For that, we internally call the method [initializeEntity].
 * As this is an internal call from [ImmutableEntityStorageImpl], the deligation won't override it and this will cause two problems:
 *   - The storage in entity will be [ImmutableEntityStorageImpl], but it should be [ReadTracker].
 *   - Since the [ImmutableEntityStorageImpl] caches entities, we'll leak entities with read tracker attached.
 *
 * The argument for deligation is clearer architecture ([ImmutableEntityStorageImpl] is not open).
 */
internal class ReadTracker private constructor(
  snapshot: ImmutableEntityStorage,
  internal val onRead: (ReadTrace) -> Unit,
) : ImmutableEntityStorageImpl(
  (snapshot as ImmutableEntityStorageImpl).entitiesByType,
  snapshot.refs,
  snapshot.indexes,
  snapshot.snapshotCache,
) {

  init {
    require(snapshot !is ReadTracker) {
      "Recursive read tracker is not supported"
    }
  }

  override fun <E : WorkspaceEntity> entities(entityClass: Class<E>): Sequence<E> {
    val trace = ReadTrace.EntitiesOfType(entityClass)
    log.trace { "Read trace of `entities` function: $trace" }
    onRead(trace)
    return super.entities(entityClass)
  }

  override fun <E : WorkspaceEntity> entityCount(entityClass: Class<E>): Int {
    val trace = ReadTrace.EntitiesOfType(entityClass)
    log.trace { "Read trace of `entitiesAmount` function: $trace" }
    onRead(trace)
    return super.entityCount(entityClass)
  }

  override fun <E : WorkspaceEntityWithSymbolicId, R : WorkspaceEntity> referrers(id: SymbolicEntityId<E>,
                                                                                  entityClass: Class<R>): Sequence<R> {
    val trace = ReadTrace.HasSymbolicLinkTo(id, entityClass)
    log.trace { "Read trace of `referrers` function: $trace" }
    onRead(trace)
    return super.referrers(id, entityClass)
  }

  override fun <E : WorkspaceEntityWithSymbolicId> resolve(id: SymbolicEntityId<E>): E? {
    val trace = ReadTrace.Resolve(id)
    log.trace { "Read trace of `resolve` function: $trace" }
    onRead(trace)
    return super.resolve(id)
  }

  override fun <E : WorkspaceEntityWithSymbolicId> contains(id: SymbolicEntityId<E>): Boolean {
    val trace = ReadTrace.Resolve(id)
    log.trace { "Read trace of `contains` function: $trace" }
    onRead(trace)
    return super.contains(id)
  }

  override fun <T> getExternalMapping(identifier: ExternalMappingKey<T>): ExternalEntityMapping<T> {
    val trace = ReadTrace.ExternalMappingAccess(identifier)
    log.trace { "Read trace of `getExternalMapping` function: $trace" }
    onRead(trace)
    return super.getExternalMapping(identifier)
  }

  override fun getVirtualFileUrlIndex(): VirtualFileUrlIndex {
    TODO("The VirtualFileUrlIndex is not supported for read tracing at the moment")
  }

  override fun entitiesBySource(sourceFilter: (EntitySource) -> Boolean): Sequence<WorkspaceEntity> {
    TODO("The entitiesBySource is not supported for read tracing at the moment")
  }

  override fun getOneChild(connectionId: ConnectionId, parent: WorkspaceEntity): WorkspaceEntity? {
    val trace = ReadTrace.SomeFieldAccess(parent.asBase().id)
    log.trace { "Read trace of `getOneChild` function: $trace" }
    onRead(trace)
    return super.getOneChild(connectionId, parent)
  }

  override fun getManyChildren(connectionId: ConnectionId, parent: WorkspaceEntity): Sequence<WorkspaceEntity> {
    val trace = ReadTrace.SomeFieldAccess(parent.asBase().id)
    log.trace { "Read trace of `getManyChildren` function: $trace" }
    onRead(trace)
    return super.getManyChildren(connectionId, parent)
  }

  override fun getParent(connectionId: ConnectionId, child: WorkspaceEntity): WorkspaceEntity? {
    val trace = ReadTrace.SomeFieldAccess(child.asBase().id)
    log.trace { "Read trace of `getParent` function: $trace" }
    onRead(trace)
    return super.getParent(connectionId, child)
  }

  override fun <T : WorkspaceEntity> initializeEntity(entityId: EntityId, newInstance: () -> T): T {
    val instance = newInstance()
    instance.asBase().onRead = this.onRead
    return instance
  }

  companion object {
    private val log = logger<ReadTracker>()

    fun trace(snapshot: ImmutableEntityStorage, action: (ImmutableEntityStorage) -> Unit): Set<ReadTrace> {
      return HashSet<ReadTrace>().also { traces ->
        val traced = ReadTracker(snapshot) { traces.add(it) }
        action(traced)
      }
    }

    fun tracedSnapshot(snapshot: ImmutableEntityStorage, addTo: LongArrayList): ReadTracker {
      return ReadTracker(snapshot) { addTo.add(it.hash) }
    }
  }
}

internal typealias ReadTraceHash = Long
internal typealias ReadTraceHashSet = LongOpenHashSet
internal fun Long.traceWithDebug(debug: String): ReadTraceHash = this

internal sealed interface ReadTrace {

  /**
   * The hash of the read trace can be used to minimize the memory usage
   *   and avoid leak of the data in the traces.
   */
  val hash: ReadTraceHash

  data class EntitiesOfType(val ofClass: Class<out WorkspaceEntity>) : ReadTrace {
    override val hash: ReadTraceHash
      get() {
        val mult = 433L // Prime number id of this trace type to distinguish hashes if they have the same args. The number was chosen randomly.
        return (mult * (mult + ofClass.toClassId())).traceWithDebug(this.toString())
      }
  }

  data class HasSymbolicLinkTo(
    val linkTo: SymbolicEntityId<WorkspaceEntityWithSymbolicId>,
    val inClass: Class<out WorkspaceEntity>,
  ) : ReadTrace {
    override val hash: ReadTraceHash
      get() {
        val mult = 569L // Prime number id of this trace type to distinguish hashes if they have the same args. The number was chosen randomly.
        var res = mult * linkTo.hashCode()
        res = mult * res + inClass.toClassId()
        return res.traceWithDebug(this.toString())
      }
  }

  data class Resolve(val link: SymbolicEntityId<WorkspaceEntityWithSymbolicId>) : ReadTrace {
    override val hash: ReadTraceHash
      get() {
        val mult = 859L // Prime number id of this trace type to distinguish hashes if they have the same args. The number was chosen randomly.
        return (mult + (mult + link.hashCode())).traceWithDebug(this.toString())
      }
  }

  /**
   * Any read from external mapping.
   * This is a very broad scope, it can be more precise later.
   */
  data class ExternalMappingAccess(val identifier: ExternalMappingKey<*>) : ReadTrace {
    override val hash: ReadTraceHash
      get() {
        val mult = 1129L // Prime number id of this trace type to distinguish hashes if they have the same args. The number was chosen randomly.
        return (mult * (mult + identifier.hashCode())).traceWithDebug(this.toString())
      }
  }

  data class SomeFieldAccess(
    internal val entityId: EntityId,
  ) : ReadTrace {
    override val hash: ReadTraceHash
      get() {
        val mult = 2833L // Prime number id of this trace type to distinguish hashes if they have the same args. The number was chosen randomly.
        return (mult * (mult + entityId)).traceWithDebug(this.toString())
      }

    override fun toString(): String {
      return "SomeFieldAccess(entityId=${entityId.asString()})"
    }
  }
}

@OptIn(EntityStorageInstrumentationApi::class)
internal fun ChangeLog.toTraces(newSnapshot: ImmutableEntityStorageInstrumentation): ReadTraceHashSet {
  val patternSet = ReadTraceHashSet()
  this@toTraces.forEach { (id, change) ->
    when (change) {
      is ChangeEntry.AddEntity -> {
        val ofClass = id.clazz.findWorkspaceEntity()
        val entityData = change.entityData

        patternSet.add(ReadTrace.EntitiesOfType(ofClass).hash)

        if (entityData is SoftLinkable) {
          entityData.getLinks().forEach { link ->
            patternSet.add(ReadTrace.HasSymbolicLinkTo(link, ofClass).hash)
          }
        }

        val entity = entityData.createEntity(newSnapshot)
        if (entity is WorkspaceEntityWithSymbolicId) {
          patternSet.add(ReadTrace.Resolve(entity.symbolicId).hash)
        }
      }
      is ChangeEntry.RemoveEntity -> {
        val ofClass = id.clazz.findWorkspaceEntity()
        val entityData = change.oldData

        patternSet.add(ReadTrace.EntitiesOfType(ofClass).hash)

        if (entityData is SoftLinkable) {
          entityData.getLinks().forEach { link ->
            patternSet.add(ReadTrace.HasSymbolicLinkTo(link, ofClass).hash)
          }
        }

        val entity = entityData.createEntity(newSnapshot)
        if (entity is WorkspaceEntityWithSymbolicId) {
          patternSet.add(ReadTrace.Resolve(entity.symbolicId).hash)
        }
      }
      is ChangeEntry.ReplaceEntity -> {
        val ofClass = id.clazz.findWorkspaceEntity()
        val entityData = (newSnapshot as ImmutableEntityStorageImpl).entityDataByIdOrDie(id)

        patternSet.add(ReadTrace.SomeFieldAccess(id).hash)

        // Becase maybe we update the field with links
        if (entityData is SoftLinkable) {
          entityData.getLinks().forEach { link ->
            patternSet.add(ReadTrace.HasSymbolicLinkTo(link, ofClass).hash)
          }
        }

        // Because maybe we update the field that calculates symbolic id
        val entity = entityData.createEntity(newSnapshot)
        if (entity is WorkspaceEntityWithSymbolicId) {
          patternSet.add(ReadTrace.Resolve(entity.symbolicId).hash)
        }
      }
    }
  }
  return patternSet
}
