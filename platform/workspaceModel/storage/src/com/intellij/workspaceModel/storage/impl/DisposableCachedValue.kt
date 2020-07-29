// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.workspaceModel.storage.CachedValue
import com.intellij.workspaceModel.storage.VersionedEntityStorage

class DisposableCachedValue<R : Disposable>(private val entityStorage: () -> VersionedEntityStorage,
                                            private val cachedValue: CachedValue<R>) : Disposable {

  private var latestValue: R? = null

  val value: R
    @Synchronized
    get() {
      val currentValue = entityStorage().cachedValue(cachedValue)

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
      latestValue = null
    }
  }
}
