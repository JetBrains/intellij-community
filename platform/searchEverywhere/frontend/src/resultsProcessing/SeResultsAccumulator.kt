// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.resultsProcessing

import com.intellij.platform.searchEverywhere.SeItemData
import com.intellij.platform.searchEverywhere.SeProviderId
import com.intellij.platform.searchEverywhere.api.SeResultAddedEvent
import com.intellij.platform.searchEverywhere.api.SeResultEvent
import com.intellij.platform.searchEverywhere.api.SeResultReplacedEvent
import com.intellij.platform.searchEverywhere.api.SeResultSkippedEvent
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.yield
import org.jetbrains.annotations.ApiStatus
import kotlin.coroutines.coroutineContext

@ApiStatus.Internal
class SeResultsAccumulator(providerIdsAndLimits: Map<SeProviderId, Int>) {
  private val mutex = Mutex()
  private val items = mutableListOf<SeItemData>()
  private val providerToCountDownLatch = HashMap<SeProviderId, SuspendableCountDownLatch>().apply {
    this.putAll(providerIdsAndLimits.map { (providerId, limit) -> providerId to SuspendableCountDownLatch(limit) })
  }

  suspend fun add(newItem: SeItemData): SeResultEvent {
    val newCountDownLatch = providerToCountDownLatch[newItem.providerId]
    newCountDownLatch?.decrementOrAwait()

    mutex.withLock {
      val event = calculateEventType(newItem)

      when (event) {
        is SeResultAddedEvent -> {
          items.add(event.itemData)
        }
        is SeResultReplacedEvent -> {
          items.remove(event.oldItemData)
          items.add(event.newItemData)
          providerToCountDownLatch[event.oldItemData.providerId]?.increment()
        }
        is SeResultSkippedEvent -> {
          newCountDownLatch?.increment()
        }
      }

      return event
    }
  }

  private fun calculateEventType(newItem: SeItemData): SeResultEvent {
    return SeResultAddedEvent(newItem) // TODO: Calculate properly
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
