// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl.reactive

import com.intellij.platform.workspace.storage.impl.VersionedStorageChangeInternal
import com.intellij.platform.workspace.storage.impl.cache.CacheProcessingStatus
import com.intellij.platform.workspace.storage.impl.cache.ChangeOnVersionedChange
import com.intellij.platform.workspace.storage.impl.cache.cache
import com.intellij.platform.workspace.storage.impl.query.Diff
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentationApi
import com.intellij.platform.workspace.storage.instrumentation.ImmutableEntityStorageInstrumentation
import com.intellij.platform.workspace.storage.query.CollectionQuery
import com.intellij.platform.workspace.storage.query.StorageQuery
import com.intellij.workspaceModel.ide.impl.WorkspaceModelImpl
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectIndexed
import kotlinx.coroutines.flow.flow
import org.jetbrains.annotations.ApiStatus

@OptIn(EntityStorageInstrumentationApi::class)
@ApiStatus.Internal
class WmReactive(private val workspaceModel: WorkspaceModelImpl) {
  suspend fun <T> flowOfQuery(query: StorageQuery<T>): Flow<T> {
    return flow {
      var cache = cache()
      workspaceModel.eventLog.collectIndexed { index, event ->
        if (index == 0) {
          val res = cache.cached(query, event.storageAfter as ImmutableEntityStorageInstrumentation, null)
          emit(res.value)
        }
        else {
          val changes = ChangeOnVersionedChange((event as VersionedStorageChangeInternal).getAllChanges ())
          val newCache = cache()
          newCache.pullCache(event.storageAfter, cache, changes)
          val cachedValue = newCache.cached(query,
                                            event.storageAfter as ImmutableEntityStorageInstrumentation,
                                            event.storageBefore as ImmutableEntityStorageInstrumentation)
          if (cachedValue.cacheProcessStatus is CacheProcessingStatus.ValueChanged) {
            emit(cachedValue.value)
          }
          cache = newCache
        }
      }
    }
  }

  suspend fun <T> flowOfNewElements(query: CollectionQuery<T>): Flow<T> {
    return flow {
      var cache = cache()
      workspaceModel.eventLog.collectIndexed { index, event ->
        if (index == 0) {
          val res = cache.diff(query, event.storageAfter as ImmutableEntityStorageInstrumentation, null)
          res.value.added.forEach {
            emit(it)
          }
        }
        else {
          val changes = ChangeOnVersionedChange((event as VersionedStorageChangeInternal).getAllChanges())
          val newCache = cache()
          newCache.pullCache(event.storageAfter, cache, changes)
          val cachedValue = newCache.diff(query,
                                          event.storageAfter as ImmutableEntityStorageInstrumentation,
                                          event.storageBefore as ImmutableEntityStorageInstrumentation)
          if (cachedValue.cacheProcessStatus is CacheProcessingStatus.ValueChanged) {
            val newState = cachedValue.value
            newState.added.forEach { emit(it) }
          }
          cache = newCache
        }
      }
    }
  }

  suspend fun <T> flowOfDiff(query: CollectionQuery<T>): Flow<Diff<T>> {
    return flow {
      var cache = cache()
      workspaceModel.eventLog.collectIndexed { index, event ->
        val changes = ChangeOnVersionedChange((event as VersionedStorageChangeInternal).getAllChanges())
        if (index == 0) {
          val res = cache.diff(query, event.storageAfter as ImmutableEntityStorageInstrumentation, null)
          emit(res.value)
        }
        else {
          val newCache = cache()
          newCache.pullCache(event.storageAfter, cache, changes)
          val cachedValue = newCache.diff(query,
                                          event.storageAfter as ImmutableEntityStorageInstrumentation,
                                          event.storageBefore as ImmutableEntityStorageInstrumentation)
          if (cachedValue.cacheProcessStatus is CacheProcessingStatus.ValueChanged) {
            emit(cachedValue.value)
          }
          cache = newCache
        }
      }
    }
  }
}
