// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.workspaceModel.storage.CachedValue
import com.intellij.workspaceModel.storage.VersionedEntityStorage
import com.intellij.workspaceModel.storage.MutableEntityStorage

class DisposableCachedValue<R : Disposable>(private val entityStorage: () -> VersionedEntityStorage,
                                            private val cachedValue: CachedValue<R>) : Disposable {

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
        } else {
          currentValue = latestValue!!
        }
      } else {
        currentValue = storage.cachedValue(cachedValue)
      }

      val oldValue = latestValue
      if (oldValue !== currentValue && oldValue != null) {
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
}
