// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data

import com.intellij.openapi.Disposable
import org.jetbrains.annotations.CalledInAwt
import java.util.*

interface GHListLoader<T> : Disposable {
  @get:CalledInAwt
  val loading: Boolean

  @get:CalledInAwt
  val error: Throwable?

  @get:CalledInAwt
  val loadedData: List<T>

  @Deprecated("Use loadedData", replaceWith = ReplaceWith("loadedData.isNotEmpty()"))
  @get:CalledInAwt
  val hasLoadedItems: Boolean

  @CalledInAwt
  fun canLoadMore(): Boolean

  @CalledInAwt
  fun loadMore(update: Boolean = false)

  @CalledInAwt
  fun updateData(item: T)

  @CalledInAwt
  fun removeData(predicate: (T) -> Boolean)

  @CalledInAwt
  fun reset()

  @CalledInAwt
  fun addLoadingStateChangeListener(disposable: Disposable, listener: () -> Unit)

  @CalledInAwt
  fun addDataListener(disposable: Disposable, listener: ListDataListener)

  @CalledInAwt
  fun addErrorChangeListener(disposable: Disposable, listener: () -> Unit)

  interface ListDataListener : EventListener {
    fun onDataAdded(startIdx: Int) {}
    fun onDataUpdated(idx: Int) {}
    fun onDataRemoved(data: Any) {}
    fun onAllDataRemoved() {}
  }
}