// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.searchEverywhere.core

import com.intellij.platform.searchEverywhere.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.yield
import org.jetbrains.annotations.ApiStatus
import kotlin.coroutines.coroutineContext

@ApiStatus.Internal
class SearchEverywhereResultsAccumulator(providerIdsAndLimits: Map<SearchEverywhereProviderId, Int>,
                                         alreadyFoundResults: List<SearchEverywhereItemData>) {

  sealed class Event
  data class Added(val itemData: SearchEverywhereItemData): Event()
  data class Replaced(val oldItemData: SearchEverywhereItemData, val newItemData: SearchEverywhereItemData): Event()
  data class None(val itemData: SearchEverywhereItemData): Event()

  private val alreadyFoundIds = alreadyFoundResults.map { idForItem(it) }.toSet()
  private val itemIdToItem = HashMap<SearchEverywhereItemId, SearchEverywhereItemData>()
  private val providerToCountDownLatch = HashMap<SearchEverywhereProviderId, SuspendableCountDownLatch>().apply {
    this.putAll(providerIdsAndLimits.map { (providerId, limit) -> providerId to SuspendableCountDownLatch(limit) })
  }

  suspend fun add(newItem: SearchEverywhereItemData): Event {
    val newId = idForItem(newItem)
    val newCountDownLatch = providerToCountDownLatch[newItem.providerId] ?: return None(newItem)
    newCountDownLatch.decrementOrAwait()

    synchronized(this) {
      val oldItem = itemIdToItem[newId]

      if (!alreadyFoundIds.contains(newId) && (oldItem == null || shouldReplace(oldItem, newItem))) {
        itemIdToItem[newId] = newItem

        if (oldItem != null) {
          providerToCountDownLatch[oldItem.providerId]?.increment()
          return Replaced(oldItem, newItem)
        }
        else {
          return Added(newItem)
        }
      }
      else {
        newCountDownLatch.increment()
        return None(newItem)
      }
    }
  }

  private fun shouldReplace(oldItem: SearchEverywhereItemData, newItem: SearchEverywhereItemData): Boolean {
    val oldId = idForItem(oldItem)
    val newId = idForItem(newItem)

    if (oldId != newId) {
      return false
    }

    if (oldItem.providerId == newItem.providerId) {
      return true
    }

    if (oldItem.presentation is OptionItemPresentation && newItem.presentation is ActionItemPresentation) {
      return true
    }

    return false
  }

  private fun idForItem(itemData: SearchEverywhereItemData): SearchEverywhereItemId {
    return itemData.itemId
  }
}

private class SuspendableCountDownLatch(initialCount: Int) {
  private var count = initialCount

  fun increment() {
    synchronized(this) {
      count++
    }
  }

  suspend fun decrementOrAwait() {
    while (coroutineContext.isActive) {
      synchronized(this) {
        if (count > 0) {
          count--
          return
        }
      }
      yield()
    }
  }
}