// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.backend.impl

import com.intellij.platform.searchEverywhere.SeItemData
import com.intellij.platform.searchEverywhere.SeProviderId
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class SeResultsCountBalancer(providerIds: List<SeProviderId>) {
  private val providerToSemaphore = HashMap<SeProviderId, Semaphore>().apply {
    this.putAll(providerIds.map { it to Semaphore(ELEMENTS_LIMIT) })
  }
  private val mutex = Mutex()
  private val runningProviders = providerIds.toMutableSet()

  suspend fun end(providerId: SeProviderId) {
    providerToSemaphore[providerId]?.acquire()
    balancePermits(providerId)
  }

  suspend fun add(newItem: SeItemData): SeItemData {
    providerToSemaphore[newItem.providerId]?.acquire()
    balancePermits()
    return newItem
  }

  private suspend fun balancePermits(providerToRemove: SeProviderId? = null) {
    mutex.withLock {
      providerToRemove?.let { runningProviders.remove(it) }
      if (runningProviders.isEmpty()) return

      val runningProvidersToAvailablePermits = runningProviders.associateWith { providerToSemaphore[it]!!.availablePermits }
      if (runningProvidersToAvailablePermits.values.all { it == 0 }) {
        runningProvidersToAvailablePermits.keys.forEach { providerId ->
          repeat(ELEMENTS_LIMIT) {
            providerToSemaphore[providerId]?.release()
          }
        }
      }
    }
  }

  companion object {
    private const val ELEMENTS_LIMIT: Int = 15
  }
}