// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.providers

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.util.Disposer
import com.intellij.platform.searchEverywhere.SeItemsProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

/**
 * A circuit breaker for slow items provider factories.
 *
 * The breaker of a factory is closed at first, and the popup waits for the provider.
 * An initialization is slow when it takes longer than [slowThreshold].
 * After [slowOpeningsToOpen] slow popup openings in a row, the breaker opens.
 * An open breaker does not call the factory, and the popup opens without the provider.
 *
 * After a cooldown, an open breaker runs one trial initialization in the background and disposes its provider.
 * A fast trial closes the breaker. A slow trial doubles the cooldown, up to [maxCooldownOpenings].
 * The cooldown is a number of popup openings and starts with one opening.
 *
 * One popup opening can call a factory several times. The guard counts each opening only once.
 */
@ApiStatus.Internal
class SeProviderInitializationGuard(
  private val coroutineScope: CoroutineScope,
  private val slowThreshold: Duration,
  private val slowOpeningsToOpen: Int,
  private val maxCooldownOpenings: Int,
) {
  private val states = ConcurrentHashMap<String, FactoryState>()

  /**
   * @param openingId identifies the popup opening. All calls with an equal [openingId] belong to one opening.
   * @param pluginId the plugin of the factory. The guard uses it only in the log.
   */
  suspend fun initialize(
    factoryId: String,
    openingId: Any,
    pluginId: String?,
    logLabel: String,
    initializer: suspend () -> SeItemsProvider?,
  ): SeItemsProvider? {
    val state = states.computeIfAbsent(factoryId) { FactoryState() }
    val factoryName = "$factoryId (plugin: $pluginId, $logLabel)"

    when (synchronized(state) { state.decide(openingId) }) {
      Decision.WAIT -> {
        val start = TimeSource.Monotonic.markNow()
        val provider = initializer()
        val elapsed = start.elapsedNow()
        synchronized(state) { state.recordInitialization(openingId, elapsed, factoryName) }
        return provider
      }
      Decision.SKIP -> {
        SeLog.log(SeLog.LIFE_CYCLE) { "Items provider $factoryName is skipped: the circuit breaker is open" }
        return null
      }
      Decision.TRIAL -> {
        SeLog.log(SeLog.LIFE_CYCLE) { "Items provider $factoryName is skipped: a trial initialization runs in the background" }
        coroutineScope.launch(currentCoroutineContext().minusKey(Job) + Dispatchers.Default) {
          var provider: SeItemsProvider? = null
          var elapsed = Duration.INFINITE
          try {
            val start = TimeSource.Monotonic.markNow()
            provider = initializer()
            elapsed = start.elapsedNow()
          }
          finally {
            synchronized(state) { state.recordTrial(elapsed, factoryName) }
            provider?.let { Disposer.dispose(it) }
          }
        }
        return null
      }
    }
  }

  private enum class Decision { WAIT, SKIP, TRIAL }

  private inner class FactoryState {
    private var lastOpeningId: Any? = null
    private var lastSlowOpeningId: Any? = null
    private var slowOpenings = 0
    private var isOpen = false
    private var cooldownOpenings = 0
    private var skippedOpenings = 0
    private var isTrialRunning = false

    fun decide(openingId: Any): Decision {
      val isNewOpening = lastOpeningId != openingId
      lastOpeningId = openingId
      if (!isOpen) return Decision.WAIT
      if (isTrialRunning) return Decision.SKIP

      if (isNewOpening) skippedOpenings++
      if (skippedOpenings < cooldownOpenings) return Decision.SKIP

      isTrialRunning = true
      return Decision.TRIAL
    }

    fun recordInitialization(openingId: Any, elapsed: Duration, factoryName: String) {
      if (elapsed <= slowThreshold) {
        // A fast call does not reset the count when an earlier call of the same opening was slow.
        if (lastSlowOpeningId != openingId) slowOpenings = 0
        return
      }
      if (lastSlowOpeningId == openingId || isOpen) return

      lastSlowOpeningId = openingId
      slowOpenings++
      if (slowOpenings < slowOpeningsToOpen) {
        SeLog.warn("Items provider $factoryName initialized in ${elapsed.inWholeMilliseconds} ms. " +
                   "Slow openings in a row: $slowOpenings of $slowOpeningsToOpen")
        return
      }

      isOpen = true
      cooldownOpenings = 1
      skippedOpenings = 0
      SeLog.warn("Items provider $factoryName initialized in ${elapsed.inWholeMilliseconds} ms. " +
                 "The circuit breaker opens after $slowOpenings slow openings in a row. " +
                 "Search Everywhere skips the provider and tries it again in the background after $cooldownOpenings opening")
    }

    fun recordTrial(elapsed: Duration, factoryName: String) {
      isTrialRunning = false
      if (elapsed <= slowThreshold) {
        isOpen = false
        slowOpenings = 0
        cooldownOpenings = 0
        SeLog.warn("Items provider $factoryName initialized in ${elapsed.inWholeMilliseconds} ms in the trial. " +
                   "The circuit breaker closes")
        return
      }

      cooldownOpenings = (cooldownOpenings * 2).coerceAtMost(maxCooldownOpenings)
      skippedOpenings = 0
      val time = if (elapsed.isInfinite()) "did not initialize" else "initialized in ${elapsed.inWholeMilliseconds} ms"
      SeLog.warn("Items provider $factoryName $time in the trial. " +
                 "The circuit breaker stays open. The next trial runs after $cooldownOpenings openings")
    }
  }

  companion object {
    val DEFAULT_SLOW_THRESHOLD: Duration = 500.milliseconds
    const val DEFAULT_SLOW_OPENINGS_TO_OPEN: Int = 2
    const val DEFAULT_MAX_COOLDOWN_OPENINGS: Int = 32

    fun getInstance(): SeProviderInitializationGuard = service<SeProviderInitializationGuardHolder>().guard
  }
}

@Service(Service.Level.APP)
internal class SeProviderInitializationGuardHolder(coroutineScope: CoroutineScope) {
  val guard = SeProviderInitializationGuard(coroutineScope,
                                            SeProviderInitializationGuard.DEFAULT_SLOW_THRESHOLD,
                                            SeProviderInitializationGuard.DEFAULT_SLOW_OPENINGS_TO_OPEN,
                                            SeProviderInitializationGuard.DEFAULT_MAX_COOLDOWN_OPENINGS)
}
