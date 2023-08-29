// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.impl.cache

import com.intellij.platform.workspace.storage.EntityChange
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentationApi
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageSnapshotInstrumentation
import com.intellij.platform.workspace.storage.query.StorageQuery

@OptIn(EntityStorageInstrumentationApi::class)
internal interface SnapshotCache {
  fun initSnapshot(snapshot: EntityStorageSnapshotInstrumentation)
  fun <T> cached(query: StorageQuery<T>): T
  fun pullCache(from: SnapshotCache, changes: Map<Class<*>, List<EntityChange<*>>>)
}
