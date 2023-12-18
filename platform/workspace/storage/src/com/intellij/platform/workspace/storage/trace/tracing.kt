// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.platform.workspace.storage.trace

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.impl.*
import com.intellij.platform.workspace.storage.impl.containers.ClosableHashSet
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentationApi
import com.intellij.platform.workspace.storage.instrumentation.ImmutableEntityStorageInstrumentation
import com.intellij.platform.workspace.storage.url.VirtualFileUrlIndex
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import java.util.*


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
      return ClosableHashSet<ReadTrace>().use { traces ->
        val traced = ReadTracker(snapshot) { traces.add(it) }
        action(traced)
        traces
      }
    }

    fun <T> traceHashes(snapshot: ImmutableEntityStorage, action: (ImmutableEntityStorage) -> T): Pair<ReadTraceHashSet, T> {
      val res = ClosableHashSet<ReadTraceHash>().use { traces ->
        val traced = ReadTracker(snapshot) { traces.add(it.hash) }
        val res = action(traced)
        traces to res
      }
      return ReadTraceHashSet(res.first) to res.second
    }
  }
}

// Type aliases for read trace as Int with string. This should increase memory consumption, but make debugging simpler
internal data class ReadTraceHash(val hash: Int, val debug: String)
internal typealias ReadTraceHashSet = HashSet<ReadTraceHash>
internal fun Int.traceWithDebug(debug: String): ReadTraceHash = ReadTraceHash(this, debug)
internal typealias ObjectToTraceMap<K, V> = HashMap<K, V>
internal typealias TraceToObjectMap<K, V> = Object2ObjectOpenHashMap<K, V>

// Type aliases for read trace as Int. This should decrease memory consumption, but make debugging more complicated
//internal typealias ReadTraceHash = Int
//internal typealias ReadTraceHashSet = IntOpenHashSet
//internal fun Int.traceWithDebug(debug: String): ReadTraceHash = this
//internal typealias ObjectToTraceMap<K, V> = HashMap<K, IntSet>
//internal typealias TraceToObjectMap<K, V> = Int2ObjectOpenHashMap<V>


internal sealed interface ReadTrace {

  /**
   * The hash of the read trace can be used to minimize the memory usage
   *   and avoid leak of the data in the traces.
   */
  val hash: ReadTraceHash

  data class EntitiesOfType(val ofClass: Class<out WorkspaceEntity>) : ReadTrace {
    override val hash: ReadTraceHash
      get() {
        return Objects.hash(
          433, // Prime number id of this trace type to distinguish hashes if they have the same args. The number was chosen randomly.
          ofClass.toClassId(), // toClassId makes hashing more stable as the hash won't depend on class loading
        ).traceWithDebug(this.toString())
      }
  }

  data class HasSymbolicLinkTo(
    val linkTo: SymbolicEntityId<WorkspaceEntityWithSymbolicId>,
    val inClass: Class<out WorkspaceEntity>,
  ) : ReadTrace {
    override val hash: ReadTraceHash
      get() {
        return Objects.hash(
          569, // Prime number id of this trace type to distinguish hashes if they have the same args. The number was chosen randomly.
          linkTo,
          inClass.toClassId(), // toClassId makes hashing more stable as the hash won't depend on class loading
        ).traceWithDebug(this.toString())
      }
  }

  data class Resolve(val link: SymbolicEntityId<WorkspaceEntityWithSymbolicId>) : ReadTrace {
    override val hash: ReadTraceHash
      get() {
        return Objects.hash(
          859, // Prime number id of this trace type to distinguish hashes if they have the same args. The number was chosen randomly.
          link,
        ).traceWithDebug(this.toString())
      }
  }

  /**
   * Any read from external mapping.
   * This is a very broad scope, it can be more precise later.
   */
  data class ExternalMappingAccess(val identifier: ExternalMappingKey<*>) : ReadTrace {
    override val hash: ReadTraceHash
      get() {
        return Objects.hash(
          1129, // Prime number id of this trace type to distinguish hashes if they have the same args. The number was chosen randomly.
          identifier,
        ).traceWithDebug(this.toString())
      }
  }

  data class SomeFieldAccess(
    internal val entityId: EntityId,
  ) : ReadTrace {
    override val hash: ReadTraceHash
      get() {
        return Objects.hash(
          2833, // Prime number id of this trace type to distinguish hashes if they have the same args. The number was chosen randomly.
          entityId,
        ).traceWithDebug(this.toString())
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
