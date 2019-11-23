package com.intellij.workspace.api

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer

class DisposableCachedValue<R : Disposable>(private val entityStore: () -> TypedEntityStore,
                                            private val cachedValue: CachedValue<R>) : Disposable {

  private var latestValue: R? = null

  val value: R
    @Synchronized
    get() {
      val currentValue = entityStore().cachedValue(cachedValue)

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
      entityStore().clearCachedValue(cachedValue)
      Disposer.dispose(oldValue)
      latestValue = null
    }
  }
}
