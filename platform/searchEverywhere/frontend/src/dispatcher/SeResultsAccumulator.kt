// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.dispatcher

import com.intellij.platform.searchEverywhere.SeItemData
import com.intellij.platform.searchEverywhere.SeItemId
import com.intellij.platform.searchEverywhere.SeProviderId
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.yield
import org.jetbrains.annotations.ApiStatus
import kotlin.coroutines.coroutineContext

@ApiStatus.Internal
class SeResultsAccumulator(providerIdsAndLimits: Map<SeProviderId, Int>,
                           alreadyFoundResults: List<SeItemData>) {

  class Event(val oldItemData: SeItemData?, val newItemData: SeItemData?) {
    val isAdded: Boolean get() = oldItemData == null && newItemData != null
    val isReplaced: Boolean get() = oldItemData != null && newItemData != null
    val isNone: Boolean get() = oldItemData == null && newItemData == null
  }

  private val mutex = Mutex()
  private val alreadyFoundIds = alreadyFoundResults.map { idForItem(it) }.toSet()
  private val itemIdToItem = HashMap<SeItemId, SeItemData>()
  private val providerToCountDownLatch = HashMap<SeProviderId, SuspendableCountDownLatch>().apply {
    this.putAll(providerIdsAndLimits.map { (providerId, limit) -> providerId to SuspendableCountDownLatch(limit) })
  }

  suspend fun add(newItem: SeItemData): Event {
    val newId = idForItem(newItem)
    val newCountDownLatch = providerToCountDownLatch[newItem.providerId]
    newCountDownLatch?.decrementOrAwait()

    mutex.withLock {
      val oldItem = itemIdToItem[newId]

      if (!alreadyFoundIds.contains(newId) && (oldItem == null || shouldReplace(oldItem, newItem))) {
        itemIdToItem[newId] = newItem

        if (oldItem != null) {
          providerToCountDownLatch[oldItem.providerId]?.increment()
          return Event(oldItem, newItem)
        }
        else {
          return Event(null, newItem)
        }
      }
      else {
        newCountDownLatch?.increment()
        return Event(null, null)
      }
    }
  }

  private fun shouldReplace(oldItem: SeItemData, newItem: SeItemData): Boolean {
    val oldId = idForItem(oldItem)
    val newId = idForItem(newItem)

    if (oldId != newId) {
      return false
    }

    if (oldItem.providerId == newItem.providerId) {
      return true
    }

    return false
  }

  private fun idForItem(itemData: SeItemData): SeItemId {
    return itemData.itemId
  }
}

private class SuspendableCountDownLatch(initialCount: Int) {
  private var count = initialCount
  private val mutex = Mutex()

  suspend fun increment() {
    mutex.withLock {
      count++
    }
  }

  suspend fun decrementOrAwait() {
    while (coroutineContext.isActive) {
      mutex.withLock {
        if (count > 0) {
          count--
          return
        }
      }
      yield()
    }
  }
}
