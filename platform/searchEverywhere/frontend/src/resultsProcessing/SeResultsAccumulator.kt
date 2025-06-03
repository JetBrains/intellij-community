// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.resultsProcessing

import com.intellij.platform.searchEverywhere.*
import com.intellij.platform.searchEverywhere.providers.topHit.SeTopHitItemsProvider
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class SeResultsAccumulator(providerIdsAndLimits: Map<SeProviderId, Int>) {
  private val mutex = Mutex()
  private val items = mutableMapOf<String, SeItemData>()

  private val providerToSemaphore = HashMap<SeProviderId, Semaphore>().apply {
    this.putAll(providerIdsAndLimits.map { (providerId, limit) -> providerId to Semaphore(limit) })
  }

  suspend fun add(newItem: SeItemData): SeResultEvent? {
    val providerSemaphore = providerToSemaphore[newItem.providerId]
    providerSemaphore?.acquire()

    mutex.withLock {
      val event = calculateEventType(newItem)

      when (event) {
        is SeResultAddedEvent -> {
          items[event.itemData.uuid] = event.itemData
        }
        is SeResultReplacedEvent -> {
          event.uuidsToReplace.forEach {
            items.remove(it)
          }

          items[event.newItemData.uuid] = event.newItemData

          event.uuidsToReplace.mapNotNull {
            items[it]
          }.forEach {
            providerToSemaphore[it.providerId]?.release()
          }
        }
        null -> {
          providerSemaphore?.release()
        }
      }

      return event
    }
  }

  private fun calculateEventType(newItem: SeItemData): SeResultEvent? {
    val topHitUuidToReplace =
      if (newItem.providerId.isTopHit()) {
        // Handle TopHit items: frontend items have higher priority, and they are preferred over backend items.
        items.values.firstOrNull {
          it.providerId.isTopHit() && it.presentation.text == newItem.presentation.text
        }?.let { oldItem ->
          if (newItem.weight > oldItem.weight) oldItem.uuid
          else null
        }
      }
      else null

    val toReplace = topHitUuidToReplace?.let {
      newItem.uuidsToReplace + it
    } ?: newItem.uuidsToReplace

    return if (toReplace.isNotEmpty()) {
      SeResultReplacedEvent(toReplace, newItem)
    }
    else SeResultAddedEvent(newItem)
  }

  private fun SeProviderId.isTopHit(): Boolean =
    value == SeTopHitItemsProvider.id(true) || value == SeTopHitItemsProvider.id(false)
}
