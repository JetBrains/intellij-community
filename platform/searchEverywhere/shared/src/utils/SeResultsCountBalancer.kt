// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.utils

import com.intellij.platform.searchEverywhere.SeItemData
import com.intellij.platform.searchEverywhere.SeProviderId
import com.intellij.platform.searchEverywhere.providers.SeLog
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.atomic.AtomicBoolean

@ApiStatus.Internal
class SeResultsCountBalancer(private val logLabel: String, highPriorityProviderIds: Collection<SeProviderId>, lowPriorityProviderIds: Collection<SeProviderId>) {
  private val mutex = Mutex()

  private val highPriorityRunning = highPriorityProviderIds.toMutableSet()
  private val highPriorityPermits = HashMap<SeProviderId, Semaphore>().apply {
    this.putAll(highPriorityProviderIds.map { it to Semaphore(ELEMENTS_LIMIT) })
  }

  private val lowPriorityRunning = lowPriorityProviderIds.toMutableSet()
  private val lowPriorityPermits = HashMap<SeProviderId, RelaxedSemaphore>().apply {
    this.putAll(lowPriorityProviderIds.map { it to RelaxedSemaphore(ELEMENTS_LIMIT) })
  }

  suspend fun end(providerId: SeProviderId) {
    highPriorityPermits[providerId]?.acquire() ?: lowPriorityPermits[providerId]?.acquire()
    balancePermits(providerId)
  }

  suspend fun add(newItem: SeItemData): SeItemData {
    highPriorityPermits[newItem.providerId]?.acquire() ?: lowPriorityPermits[newItem.providerId]?.acquire()
    balancePermits()
    return newItem
  }

  private suspend fun balancePermits(providerToRemove: SeProviderId? = null) {
    mutex.withLock {
      providerToRemove?.let {
        highPriorityRunning.remove(it)
        lowPriorityRunning.remove(it)
      }

      if (highPriorityRunning.isEmpty()) {
        lowPriorityRunning.forEach { lowPriorityPermits[it]?.makeItFreeToGo() }
        reportPermits()
        return
      }

      val highPriorityToAvailablePermits = highPriorityRunning.associateWith { highPriorityPermits[it]!!.availablePermits }

      if (highPriorityToAvailablePermits.values.all { it == 0 }) {
        highPriorityToAvailablePermits.keys.forEach { providerId ->
          repeat(ELEMENTS_LIMIT) {
            highPriorityPermits[providerId]?.release()
          }
        }

        lowPriorityRunning.forEach { providerId ->
          lowPriorityPermits[providerId]?.release(ELEMENTS_LIMIT)
        }
      }

      reportPermits()
    }
  }

  private suspend fun reportPermits() {
    SeLog.logSuspendable(SeLog.BALANCING) {
      if (highPriorityRunning.isEmpty()) {
        return@logSuspendable "($logLabel) No running high priority providers."
      }

      val highPriority = highPriorityRunning.associateWith { highPriorityPermits[it]!!.availablePermits }.map { "${it.key.value}: ${it.value}" }.joinToString(", ")
      val lowPriority = lowPriorityRunning.associateWith { lowPriorityPermits[it]!!.availablePermits() }.map { "${it.key.value}: ${it.value}" }.joinToString(", ")
      "($logLabel) Available permits: high: $highPriority, low: $lowPriority"
    }
  }

  companion object {
    private const val ELEMENTS_LIMIT: Int = 15
  }
}

@ApiStatus.Internal
private class RelaxedSemaphore(permits: Int) {
  private val mutex = Mutex()
  private var availablePermits = permits
  private var isFreeToGo = AtomicBoolean(false)

  suspend fun acquire() {
    if (isFreeToGo.get()) return

    mutex.withLock {
      if (isFreeToGo.get()) return

      while (!isFreeToGo.get() && availablePermits <= 0) {
        mutex.unlock()
        // Give other coroutines a chance to execute and possibly release permits
        kotlinx.coroutines.yield()
        mutex.lock()
      }
      availablePermits--
    }
  }

  suspend fun release(permits: Int = 1) {
    mutex.withLock {
      availablePermits += permits
    }
  }

  suspend fun makeItFreeToGo() {
    mutex.withLock {
      isFreeToGo.set(true)
    }
  }

  suspend fun availablePermits(): Int = mutex.withLock {
    if (isFreeToGo.get()) Int.MAX_VALUE else availablePermits
  }
}
