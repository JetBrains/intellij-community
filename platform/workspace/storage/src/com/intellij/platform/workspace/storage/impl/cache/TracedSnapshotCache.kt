// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.impl.cache

import com.intellij.platform.workspace.storage.EntityChange
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentationApi
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageSnapshotInstrumentation
import com.intellij.platform.workspace.storage.query.StorageQuery

internal interface EntityStorageCache {
  fun init(storage: EntityStorage)
  fun <T> cached(query: StorageQuery<T>): T
  fun isEmpty(): Boolean
}

@OptIn(EntityStorageInstrumentationApi::class)
internal interface TracedSnapshotCache {
  fun initSnapshot(snapshot: EntityStorageSnapshotInstrumentation)
  fun <T> cached(query: StorageQuery<T>): T
  fun pullCache(from: TracedSnapshotCache, changes: Map<Class<*>, List<EntityChange<*>>>)
}
