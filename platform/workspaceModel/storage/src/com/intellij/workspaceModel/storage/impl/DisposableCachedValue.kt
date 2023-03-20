// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.storage.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.Disposer
import com.intellij.workspaceModel.storage.CachedValue
import com.intellij.workspaceModel.storage.MutableEntityStorage
import com.intellij.workspaceModel.storage.VersionedEntityStorage

class DisposableCachedValue<R : Disposable>(
  private val entityStorage: () -> VersionedEntityStorage,
  private val cachedValue: CachedValue<R>,
  private val cacheName: String = "-",
) : Disposable {

  private var latestValue: R? = null
  private var latestStorageModificationCount: Long? = null

  val value: R
    @Synchronized
    get() {
      val currentValue: R
      val storage = entityStorage()
      if (storage is DummyVersionedEntityStorage) {
        val storageModificationCount = (storage.current as MutableEntityStorage).modificationCount
        if (storageModificationCount != latestStorageModificationCount) {
          currentValue = storage.cachedValue(cachedValue)
          latestStorageModificationCount = storageModificationCount
        }
        else {
          currentValue = latestValue!!
        }
      }
      else {
        currentValue = storage.cachedValue(cachedValue)
      }

      val oldValue = latestValue
      if (oldValue !== currentValue && oldValue != null) {
        log.debug { "Dispose old value. Cache name: `$cacheName`. Store type: ${storage.javaClass}. Store version: ${storage.version}" }
        Disposer.dispose(oldValue)
      }
      latestValue = currentValue

      return currentValue
    }

  override fun dispose() {
    dropCache()
  }

  @Synchronized
  fun dropCache() {
    val oldValue = latestValue
    if (oldValue != null) {
      entityStorage().clearCachedValue(cachedValue)
      Disposer.dispose(oldValue)
      latestStorageModificationCount = null
      latestValue = null
    }
  }

  companion object {
    private val log = logger<DisposableCachedValue<*>>()
  }
}
