// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.utils

import com.intellij.platform.searchEverywhere.SeItemData
import com.intellij.platform.searchEverywhere.SeProviderId
import com.intellij.platform.searchEverywhere.providers.SeLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import org.jetbrains.annotations.ApiStatus
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.fetchAndIncrement

/**
 * Balances the number of results from different providers.
 *
 *    nonBlockedProviderIds - providers that are not limited, they can produce any number of the results without being blocked.
 *
 *    highPriorityProviderIds - providers that are limited by the maximum number of results produced by any of the providers from nonBlockedProviderIds,
 *        and by a condition that the difference between the numbers of results produced by providers from highPriorityProviderIds is less than or equal to DIFFERENCE_LIMIT.
 *
 *    lowPriorityProviderIds - providers that are limited by the maximum number of results produced by any of the providers from nonBlockedProviderIds,
 *        and by the minimum number of results produced by providers from highPriorityProviderIds, but are independent from each other
 */
@OptIn(ExperimentalAtomicApi::class)
@ApiStatus.Internal
class SeResultsCountBalancer(private val logLabel: String,
                             nonBlockedProviderIds: Collection<SeProviderId>,
                             highPriorityProviderIds: Collection<SeProviderId>,
                             lowPriorityProviderIds: Collection<SeProviderId>) {
  private val mutex = Mutex()

  private val nonBlockedRunning = nonBlockedProviderIds.toMutableSet()
  private val nonBlockedCounts = HashMap<SeProviderId, AtomicInt>().apply {
    this.putAll(nonBlockedProviderIds.map { it to AtomicInt(-DIFFERENCE_LIMIT) })
  }

  private val highPriorityRunning = highPriorityProviderIds.toMutableSet()
  private val highPriorityPermits = HashMap<SeProviderId, Semaphore>().apply {
    this.putAll(highPriorityProviderIds.map { it to Semaphore(DIFFERENCE_LIMIT) })
  }

  private val lowPriorityRunning = lowPriorityProviderIds.toMutableSet()
  private val lowPriorityPermits = HashMap<SeProviderId, RelaxedSemaphore>().apply {
    this.putAll(lowPriorityProviderIds.map { it to RelaxedSemaphore(DIFFERENCE_LIMIT) })
  }

  suspend fun end(providerId: SeProviderId) {
    highPriorityPermits[providerId]?.acquire() ?: lowPriorityPermits[providerId]?.acquire()
    balancePermits(providerId)
  }

  suspend fun add(newItem: SeItemData): SeItemData {
    highPriorityPermits[newItem.providerId]?.acquire()
    ?: lowPriorityPermits[newItem.providerId]?.acquire()
    ?: nonBlockedCounts[newItem.providerId]?.fetchAndIncrement()
    balancePermits()
    return newItem
  }

  private suspend fun balancePermits(providerToRemove: SeProviderId? = null) {
    mutex.withLock {
      providerToRemove?.let {
        nonBlockedRunning.remove(it)
        highPriorityRunning.remove(it)
        lowPriorityRunning.remove(it)
      }

      if (nonBlockedRunning.isEmpty() && highPriorityRunning.isEmpty()) {
        lowPriorityRunning.forEach { lowPriorityPermits[it]?.makeItFreeToGo() }
        reportPermits()
        return
      }

      val nonBlockedCountMaximum = nonBlockedRunning.mapNotNull { nonBlockedCounts[it]?.load() }.maxOrNull() ?: Int.MAX_VALUE
      val highPriorityToAvailablePermits = highPriorityRunning.associateWith { highPriorityPermits[it]!!.availablePermits }

      if (highPriorityToAvailablePermits.values.all { it == 0 } && nonBlockedCountMaximum >= 0) {
        nonBlockedRunning.forEach { providerId ->
          nonBlockedCounts[providerId]?.fetchAndAdd(-DIFFERENCE_LIMIT)
        }

        highPriorityToAvailablePermits.keys.forEach { providerId ->
          repeat(DIFFERENCE_LIMIT) {
            highPriorityPermits[providerId]?.release()
          }
        }

        lowPriorityRunning.forEach { providerId ->
          lowPriorityPermits[providerId]?.release(DIFFERENCE_LIMIT)
        }
      }

      reportPermits()
    }
  }

  private suspend fun reportPermits() {
    SeLog.logSuspendable(SeLog.BALANCING) {
      val nonBlocked = nonBlockedRunning.associateWith { nonBlockedCounts[it]!!.load() }.map { "${it.key.value}: ${it.value}" }.joinToString(", ")
      val highPriority = highPriorityRunning.associateWith { highPriorityPermits[it]!!.availablePermits }.map { "${it.key.value}: ${it.value}" }.joinToString(", ")
      val lowPriority = lowPriorityRunning.associateWith { lowPriorityPermits[it]!!.availablePermits }.map { "${it.key.value}: ${it.value}" }.joinToString(", ")
      "($logLabel) Available permits: nonBlocked: $nonBlocked, high: $highPriority, low: $lowPriority"
    }
  }

  companion object {
    private const val DIFFERENCE_LIMIT: Int = 15
  }
}

/**
 * A semaphore that allows releasing more permits, than the initial permits value.
 */
@ApiStatus.Internal
class RelaxedSemaphore(initialPermits: Int = 0) {
  private val permitsFlow = MutableStateFlow(initialPermits)
  val availablePermits: Int get() = permitsFlow.value

  fun release(permits: Int = 1) {
    permitsFlow.update {
      // Avoid Int overflow if `makeItFreeToGo` has been called before
      if (it > Int.MAX_VALUE - permits) Int.MAX_VALUE
      else  it + permits
    }
  }

  suspend fun acquire() {
    while (true) {
      val count = permitsFlow.first { it >= 1 } // Wait until enough permits are available
      if (permitsFlow.compareAndSet(count, count - 1)) return
    }
  }

  fun makeItFreeToGo() {
    permitsFlow.value = Int.MAX_VALUE
  }
}
