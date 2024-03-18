// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl.reactive

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
import kotlinx.coroutines.flow.flow

@OptIn(EntityStorageInstrumentationApi::class)
class WmReactive(private val workspaceModel: WorkspaceModelImpl) {
  suspend fun <T> flowOfQuery(query: StorageQuery<T>): Flow<T> {
    return flow {
      workspaceModel.subscribe { firstSnapshot, changeChannel ->
        var cache = cache()
        val res = cache.cached(query, firstSnapshot as ImmutableEntityStorageInstrumentation, null)
        emit(res.value)
        changeChannel
          .collect {
            val newCache = cache()
            val changes = ChangeOnVersionedChange(it.getAllChanges())
            newCache.pullCache(it.storageAfter, cache, changes)
            val cachedValue = newCache.cached(query,
                                              it.storageAfter as ImmutableEntityStorageInstrumentation,
                                              it.storageBefore as ImmutableEntityStorageInstrumentation)
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
      workspaceModel.subscribe { firstSnapshot, changeChannel ->
        var cache = cache()
        val res = cache.diff(query, firstSnapshot as ImmutableEntityStorageInstrumentation, null)
        res.value.added.forEach {
          emit(it)
        }
        changeChannel
          .collect { change ->
          val newCache = cache()
          val changes = ChangeOnVersionedChange(change.getAllChanges())
          newCache.pullCache(change.storageAfter, cache, changes)
            val cachedValue = newCache.diff(query,
                                            change.storageAfter as ImmutableEntityStorageInstrumentation,
                                            change.storageBefore as ImmutableEntityStorageInstrumentation)
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
      workspaceModel.subscribe { firstSnapshot, changeChannel ->
        var cache = cache()
        val res = cache.diff(query, firstSnapshot as ImmutableEntityStorageInstrumentation, null)
        emit(res.value)
        changeChannel
          .collect { change ->
            val newCache = cache()
            val changes = ChangeOnVersionedChange(change.getAllChanges())
            newCache.pullCache(change.storageAfter, cache, changes)
            val cachedValue = newCache.diff(query,
                                            change.storageAfter as ImmutableEntityStorageInstrumentation,
                                            change.storageBefore as ImmutableEntityStorageInstrumentation)
            if (cachedValue.cacheProcessStatus is CacheProcessingStatus.ValueChanged) {
              emit(cachedValue.value)
            }
            cache = newCache
          }
      }
    }
  }
}
