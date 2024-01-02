// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.impl.cache

import com.intellij.platform.workspace.storage.EntityStorageSnapshot
import com.intellij.platform.workspace.storage.impl.EntityId
import com.intellij.platform.workspace.storage.impl.WorkspaceBuilderChangeLog
import com.intellij.platform.workspace.storage.impl.cache.CacheResetTracker.cacheReset
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentationApi
import com.intellij.platform.workspace.storage.instrumentation.ImmutableEntityStorageInstrumentation
import com.intellij.platform.workspace.storage.query.StorageQuery
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
    newSnapshot: EntityStorageSnapshot,
    from: TracedSnapshotCache,
    changes: WorkspaceBuilderChangeLog,
    externalMappingChanges: Map<String, MutableSet<EntityId>>
  )
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
