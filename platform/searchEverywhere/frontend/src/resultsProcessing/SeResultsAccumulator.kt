// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.resultsProcessing

import com.intellij.platform.searchEverywhere.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class SeResultsAccumulator(providerIdsAndLimits: Map<SeProviderId, Int>) {
  private val mutex = Mutex()
  private val items = mutableListOf<SeItemData>()

  private val providerToSemaphore = HashMap<SeProviderId, Semaphore>().apply {
    this.putAll(providerIdsAndLimits.map { (providerId, limit) -> providerId to Semaphore(limit) })
  }

  suspend fun add(newItem: SeItemData): SeResultEvent {
    val providerSemaphore = providerToSemaphore[newItem.providerId]
    providerSemaphore?.acquire()

    mutex.withLock {
      val event = calculateEventType(newItem)

      when (event) {
        is SeResultAddedEvent -> {
          items.add(event.itemData)
        }
        is SeResultReplacedEvent -> {
          items.remove(event.oldItemData)
          items.add(event.newItemData)
          providerToSemaphore[event.oldItemData.providerId]?.release()
        }
        is SeResultSkippedEvent -> {
          providerSemaphore?.release()
        }
      }

      return event
    }
  }

  private fun calculateEventType(newItem: SeItemData): SeResultEvent {
    return SeResultAddedEvent(newItem) // TODO: Calculate properly
  }
}
