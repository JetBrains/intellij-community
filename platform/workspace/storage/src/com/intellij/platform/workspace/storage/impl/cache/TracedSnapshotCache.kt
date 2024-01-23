// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.impl.cache

import com.intellij.platform.workspace.storage.ExternalMappingKey
import com.intellij.platform.workspace.storage.ImmutableEntityStorage
import com.intellij.platform.workspace.storage.impl.ChangeEntry
import com.intellij.platform.workspace.storage.impl.EntityId
import com.intellij.platform.workspace.storage.impl.WorkspaceBuilderChangeLog
import com.intellij.platform.workspace.storage.impl.cache.CacheResetTracker.cacheReset
import com.intellij.platform.workspace.storage.impl.query.MatchSet
import com.intellij.platform.workspace.storage.impl.query.MatchWithEntityId
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentationApi
import com.intellij.platform.workspace.storage.instrumentation.ImmutableEntityStorageInstrumentation
import com.intellij.platform.workspace.storage.query.StorageQuery
import com.intellij.platform.workspace.storage.trace.ReadTrace
import com.intellij.platform.workspace.storage.trace.ReadTraceHashSet
import com.intellij.platform.workspace.storage.trace.toTraces
import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly

@OptIn(EntityStorageInstrumentationApi::class)
@ApiStatus.Experimental
@ApiStatus.Internal
public interface TracedSnapshotCache {

  /**
   * Thread-safe
   */
  public fun <T> cached(query: StorageQuery<T>, snapshot: ImmutableEntityStorageInstrumentation): T

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

@ApiStatus.Experimental
@ApiStatus.Internal
public sealed interface EntityStorageChange {
  public val size: Int
}

@OptIn(EntityStorageInstrumentationApi::class)
internal fun EntityStorageChange.createTraces(snapshot: ImmutableEntityStorageInstrumentation): ReadTraceHashSet {
  return when (this) {
    is ChangeOnWorkspaceBuilderChangeLog -> this.createTraces(snapshot)
  }
}

internal fun EntityStorageChange.makeTokensForDiff(): MatchSet {
  return when (this) {
    is ChangeOnWorkspaceBuilderChangeLog -> this.makeTokensForDiff()
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
  }
  return target
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

  @OptIn(EntityStorageInstrumentationApi::class)
  internal fun createTraces(snapshot: ImmutableEntityStorageInstrumentation): ReadTraceHashSet {
    val externalMappingTraces = this.externalMappingChanges.entries
      .filter { it.value.isNotEmpty() }
      .map { it.key }
      .mapTo(ReadTraceHashSet()) { ReadTrace.ExternalMappingAccess(it).hash }
    val newTraces = ReadTraceHashSet(changes.changeLog.toTraces(snapshot))
    newTraces.addAll(externalMappingTraces)
    return newTraces
  }

  internal fun makeTokensForDiff(): MatchSet {
    val matchSet = MatchSet()
    val createdAddTokens = LongOpenHashSet()
    val createdRemovedTokens = LongOpenHashSet()

    changes.changeLog.forEach { (entityId, change) ->
      when (change) {
        is ChangeEntry.AddEntity -> {
          if (createdAddTokens.add(entityId)) matchSet.addedMatch(MatchWithEntityId(entityId, null))
        }
        is ChangeEntry.RemoveEntity -> {
          if (createdRemovedTokens.add(entityId)) matchSet.removedMatch(MatchWithEntityId(entityId, null))
        }
        is ChangeEntry.ReplaceEntity -> {
          if (createdRemovedTokens.add(entityId)) matchSet.removedMatch(MatchWithEntityId(entityId, null))
          if (createdAddTokens.add(entityId)) matchSet.addedMatch(MatchWithEntityId(entityId, null))
        }
      }
    }

    externalMappingChanges.values.forEach { affectedIds ->
      affectedIds.forEach { entityId ->
        if (createdRemovedTokens.add(entityId)) matchSet.removedMatch(MatchWithEntityId(entityId, null))
        if (createdAddTokens.add(entityId)) matchSet.addedMatch(MatchWithEntityId(entityId, null))
      }
    }

    return matchSet
  }
}

/**
 * Simple tracker if cache was reset at all. Use [CacheResetTracker.enabled] to turn it on.
 * Variable [cacheReset] is set to true if cache was reset at least once for any cache.
 *
 * Should be used in tests only.
 */
@TestOnly
public object CacheResetTracker {
  public var enabled: Boolean = false
  public var cacheReset: Boolean = false

  public fun enable() {
    enabled = true
    cacheReset = false
  }
}
