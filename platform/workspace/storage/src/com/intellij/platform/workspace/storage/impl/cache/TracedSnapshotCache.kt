// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.impl.cache

import com.intellij.platform.workspace.storage.EntityChange
import com.intellij.platform.workspace.storage.ExternalMappingKey
import com.intellij.platform.workspace.storage.ImmutableEntityStorage
import com.intellij.platform.workspace.storage.impl.ChangeEntry
import com.intellij.platform.workspace.storage.impl.EntityId
import com.intellij.platform.workspace.storage.impl.WorkspaceBuilderChangeLog
import com.intellij.platform.workspace.storage.impl.asBase
import com.intellij.platform.workspace.storage.impl.cache.CacheResetTracker.cacheReset
import com.intellij.platform.workspace.storage.impl.query.Diff
import com.intellij.platform.workspace.storage.impl.query.MatchList
import com.intellij.platform.workspace.storage.impl.query.MatchWithEntityId
import com.intellij.platform.workspace.storage.instrumentation.ImmutableEntityStorageInstrumentation
import com.intellij.platform.workspace.storage.query.CollectionQuery
import com.intellij.platform.workspace.storage.query.StorageQuery
import com.intellij.platform.workspace.storage.trace.ReadTrace
import com.intellij.platform.workspace.storage.trace.ReadTraceHashSet
import com.intellij.platform.workspace.storage.trace.toTraces
import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly


@ApiStatus.Internal
public data class CachedValue<T>(
  public val cacheProcessStatus: CacheProcessingStatus,
  public val value: T,
)

@ApiStatus.Internal
public sealed interface CacheProcessingStatus {
  @ApiStatus.Internal
  public sealed interface Hit: CacheProcessingStatus
  @ApiStatus.Internal
  public sealed interface ValueChanged: CacheProcessingStatus
}
internal data object CacheHit: CacheProcessingStatus.Hit
internal data object CacheHitInSynchronized: CacheProcessingStatus.Hit
internal data object CacheHitNotAffectedByChanges: CacheProcessingStatus.Hit
internal data object IncrementalUpdate: CacheProcessingStatus.ValueChanged
internal data object Initialization: CacheProcessingStatus.ValueChanged

@ApiStatus.Experimental
@ApiStatus.Internal
public interface TracedSnapshotCache {

  /**
   * Thread-safe
   *
   * [prevStorage] should always be null for calculation of the cache. It can be not null if we perform reactive update
   */
  public fun <T> cached(query: StorageQuery<T>,
                        snapshot: ImmutableEntityStorageInstrumentation,
                        prevStorage: ImmutableEntityStorageInstrumentation?): CachedValue<T>

  /**
   * Thread-safe
   *
   * [prevStorage] should always be null for calculation of the cache. It can be not null if we perform reactive update
   */
  public fun <T> diff(query: CollectionQuery<T>,
                      snapshot: ImmutableEntityStorageInstrumentation,
                      prevStorage: ImmutableEntityStorageInstrumentation?): CachedValue<Diff<T>>

  /**
   * Not thread-safe
   */
  public fun pullCache(
    newSnapshot: ImmutableEntityStorage,
    from: TracedSnapshotCache,
    changes: EntityStorageChange,
  )

  public companion object {
    @ApiStatus.Internal
    @set:TestOnly
    public var LOG_QUEUE_MAX_SIZE: Int = 10_000
  }
}

@ApiStatus.Internal
public fun cache(): TracedSnapshotCache {
  return TracedSnapshotCacheImpl()
}

@ApiStatus.Experimental
@ApiStatus.Internal
public sealed interface EntityStorageChange {
  public val size: Int
}

internal fun EntityStorageChange.createTraces(snapshot: ImmutableEntityStorageInstrumentation): ReadTraceHashSet {
  return when (this) {
    is ChangeOnWorkspaceBuilderChangeLog -> this.createTraces(snapshot)
    is ChangeOnVersionedChange -> this.createTraces(snapshot)
  }
}

internal fun EntityStorageChange.makeTokensForDiff(): MatchList {
  return when (this) {
    is ChangeOnWorkspaceBuilderChangeLog -> this.makeTokensForDiff()
    is ChangeOnVersionedChange -> this.makeTokensForDiff()
  }
}

internal fun List<EntityStorageChange>.collapse(): EntityStorageChange {
  if (this.isEmpty()) error("Nothing to collapse")
  if (this.size == 1) return this[0]
  val firstChange = this[0]
  val target: EntityStorageChange = when (firstChange) {
    is ChangeOnWorkspaceBuilderChangeLog -> {
      val targetChangelog = WorkspaceBuilderChangeLog()
      val targetMap = HashMap<ExternalMappingKey<*>, MutableSet<EntityId>>()
      this.forEach {
        check(it is ChangeOnWorkspaceBuilderChangeLog)
        it.addTo(targetChangelog, targetMap)
      }
      ChangeOnWorkspaceBuilderChangeLog(targetChangelog, targetMap)
    }
    is ChangeOnVersionedChange -> {
      if (this.size > 1) error("We should not collect more than one changelog")
      firstChange
    }
  }
  return target
}

@ApiStatus.Internal
public class ChangeOnVersionedChange(
  private val changes: Sequence<EntityChange<*>>,
) : EntityStorageChange {
  override val size: Int
    get() = 0 // We should not collect more than one changelog, so there is no need to analyze the size

  internal fun createTraces(snapshot: ImmutableEntityStorageInstrumentation): ReadTraceHashSet = changes.toTraces(snapshot)

  internal fun makeTokensForDiff(): MatchList {
    val matchList = MatchList()
    val createdAddTokens = LongOpenHashSet()
    val createdRemovedTokens = LongOpenHashSet()

    changes.forEach { change ->
      val entityId = change.newEntity?.asBase()?.id ?:change.oldEntity?.asBase()?.id!!
      when (change) {
        is EntityChange.Added<*> -> {
          if (createdAddTokens.add(entityId)) matchList.addedMatch(MatchWithEntityId(entityId, null))
        }
        is EntityChange.Removed<*> -> {
          if (createdRemovedTokens.add(entityId)) matchList.removedMatch(MatchWithEntityId(entityId, null))
        }
        is EntityChange.Replaced<*> -> {
          if (createdRemovedTokens.add(entityId)) matchList.removedMatch(MatchWithEntityId(entityId, null))
          if (createdAddTokens.add(entityId)) matchList.addedMatch(MatchWithEntityId(entityId, null))
        }
      }
    }

    return matchList
  }
}

internal class ChangeOnWorkspaceBuilderChangeLog(
  private val changes: WorkspaceBuilderChangeLog,
  private val externalMappingChanges: Map<ExternalMappingKey<*>, MutableSet<EntityId>>,
) : EntityStorageChange {
  override val size: Int
    get() = changes.changeLog.size + externalMappingChanges.size

  internal fun addTo(changes: WorkspaceBuilderChangeLog, externalMappingChanges: HashMap<ExternalMappingKey<*>, MutableSet<EntityId>>) {
    changes.join(this.changes)

    this.externalMappingChanges.forEach { (key, log) ->
      val existingLog = externalMappingChanges.getOrPut(key) { HashSet() }
      log.forEach { affectedEntityId ->
        existingLog.add(affectedEntityId)
      }
    }
  }

  internal fun createTraces(snapshot: ImmutableEntityStorageInstrumentation): ReadTraceHashSet {
    val externalMappingTraces = this.externalMappingChanges.entries
      .filter { it.value.isNotEmpty() }
      .map { it.key }
      .mapTo(ReadTraceHashSet()) { ReadTrace.ExternalMappingAccess(it).hash }
    val newTraces = ReadTraceHashSet(changes.changeLog.toTraces(snapshot))
    newTraces.addAll(externalMappingTraces)
    return newTraces
  }

  internal fun makeTokensForDiff(): MatchList {
    val matchList = MatchList()
    val createdAddTokens = LongOpenHashSet()
    val createdRemovedTokens = LongOpenHashSet()

    changes.changeLog.forEach { (entityId, change) ->
      when (change) {
        is ChangeEntry.AddEntity -> {
          if (createdAddTokens.add(entityId)) matchList.addedMatch(MatchWithEntityId(entityId, null))
        }
        is ChangeEntry.RemoveEntity -> {
          if (createdRemovedTokens.add(entityId)) matchList.removedMatch(MatchWithEntityId(entityId, null))
        }
        is ChangeEntry.ReplaceEntity -> {
          if (createdRemovedTokens.add(entityId)) matchList.removedMatch(MatchWithEntityId(entityId, null))
          if (createdAddTokens.add(entityId)) matchList.addedMatch(MatchWithEntityId(entityId, null))
        }
      }
    }

    externalMappingChanges.values.forEach { affectedIds ->
      affectedIds.forEach { entityId ->
        if (createdRemovedTokens.add(entityId)) matchList.removedMatch(MatchWithEntityId(entityId, null))
        if (createdAddTokens.add(entityId)) matchList.addedMatch(MatchWithEntityId(entityId, null))
      }
    }

    return matchList
  }
}

/**
 * Simple tracker if cache was reset at all. Use [CacheResetTracker.enabled] to turn it on.
 * Variable [cacheReset] is set to true if cache was reset at least once for any cache.
 *
 * Should be used in tests only.
 */
@TestOnly
@ApiStatus.Internal
public object CacheResetTracker {
  public var enabled: Boolean = false
  public var cacheReset: Boolean = false

  public fun enable() {
    enabled = true
    cacheReset = false
  }
}
