// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.impl.cache

import com.intellij.platform.workspace.storage.ExternalMappingKey
import com.intellij.platform.workspace.storage.ImmutableEntityStorage
import com.intellij.platform.workspace.storage.impl.ChangeEntry
import com.intellij.platform.workspace.storage.impl.EntityId
import com.intellij.platform.workspace.storage.impl.WorkspaceBuilderChangeLog
import com.intellij.platform.workspace.storage.impl.cache.CacheResetTracker.cacheReset
import com.intellij.platform.workspace.storage.impl.query.Operation
import com.intellij.platform.workspace.storage.impl.query.Token
import com.intellij.platform.workspace.storage.impl.query.TokenSet
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentationApi
import com.intellij.platform.workspace.storage.instrumentation.ImmutableEntityStorageInstrumentation
import com.intellij.platform.workspace.storage.query.StorageQuery
import com.intellij.platform.workspace.storage.trace.ReadTrace
import com.intellij.platform.workspace.storage.trace.ReadTraceHashSet
import com.intellij.platform.workspace.storage.trace.toTraces
import org.jetbrains.annotations.TestOnly

@OptIn(EntityStorageInstrumentationApi::class)
internal interface TracedSnapshotCache {

  /**
   * Thread-safe
   */
  fun <T> cached(query: StorageQuery<T>, snapshot: ImmutableEntityStorageInstrumentation): T

  /**
   * Not thread-safe
   */
  fun pullCache(
    newSnapshot: ImmutableEntityStorage,
    from: TracedSnapshotCache,
    changes: EntityStorageChange,
  )
}

public sealed interface EntityStorageChange {
  public val size: Int
}

@OptIn(EntityStorageInstrumentationApi::class)
internal fun EntityStorageChange.createTraces(snapshot: ImmutableEntityStorageInstrumentation): ReadTraceHashSet {
  return when (this) {
    is ChangeOnWorkspaceBuilderChangeLog -> this.createTraces(snapshot)
  }
}

internal fun EntityStorageChange.makeTokensForDiff(): TokenSet {
  return when (this) {
    is ChangeOnWorkspaceBuilderChangeLog -> this.makeTokensForDiff()
  }
}

internal fun List<EntityStorageChange>.collapse(): EntityStorageChange {
  if (this.isEmpty()) error("Nothing to collapse")
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

  internal fun makeTokensForDiff(): TokenSet {
    val tokenSet = TokenSet()
    val createdTokens = HashSet<Pair<Operation, EntityId>>()

    changes.changeLog.forEach { (entityId, change) ->
      when (change) {
        is ChangeEntry.AddEntity -> {
          if (createdTokens.add(Operation.ADDED to entityId)) tokenSet += Token.WithEntityId(Operation.ADDED, entityId)
        }
        is ChangeEntry.RemoveEntity -> {
          if (createdTokens.add(Operation.REMOVED to entityId)) tokenSet += Token.WithEntityId(Operation.REMOVED, entityId)
        }
        is ChangeEntry.ReplaceEntity -> {
          if (createdTokens.add(Operation.REMOVED to entityId)) tokenSet += Token.WithEntityId(Operation.REMOVED, entityId)
          if (createdTokens.add(Operation.ADDED to entityId)) tokenSet += Token.WithEntityId(Operation.ADDED, entityId)
        }
      }
    }

    externalMappingChanges.values.forEach { affectedIds ->
      affectedIds.forEach { entityId ->
        if (createdTokens.add(Operation.REMOVED to entityId)) tokenSet += Token.WithEntityId(Operation.REMOVED, entityId)
        if (createdTokens.add(Operation.ADDED to entityId)) tokenSet += Token.WithEntityId(Operation.ADDED, entityId)
      }
    }

    return tokenSet
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
