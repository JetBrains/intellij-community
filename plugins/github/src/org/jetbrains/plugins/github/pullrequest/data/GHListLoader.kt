// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data

import com.intellij.openapi.Disposable
import com.intellij.util.concurrency.annotations.RequiresEdt
import java.util.*

interface GHListLoader<T> : Disposable {
  @get:RequiresEdt
  val loading: Boolean

  @get:RequiresEdt
  val error: Throwable?

  @get:RequiresEdt
  val loadedData: List<T>

  @RequiresEdt
  fun canLoadMore(): Boolean

  @RequiresEdt
  fun loadMore(update: Boolean = false)

  @RequiresEdt
  fun updateData(updater: (T) -> T?)

  @RequiresEdt
  fun removeData(predicate: (T) -> Boolean)

  @RequiresEdt
  fun reset()

  @RequiresEdt
  fun addLoadingStateChangeListener(disposable: Disposable, listener: () -> Unit)

  @RequiresEdt
  fun addDataListener(disposable: Disposable, listener: ListDataListener)

  @RequiresEdt
  fun addErrorChangeListener(disposable: Disposable, listener: () -> Unit)

  @JvmDefaultWithCompatibility
  interface ListDataListener : EventListener {
    fun onDataAdded(startIdx: Int) {}
    fun onDataUpdated(idx: Int) {}
    fun onDataRemoved(idx: Int) {}
    fun onAllDataRemoved() {}
  }
}