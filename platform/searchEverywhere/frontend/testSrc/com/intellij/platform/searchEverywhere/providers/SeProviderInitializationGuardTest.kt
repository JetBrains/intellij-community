// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.providers

import com.intellij.platform.searchEverywhere.SeItem
import com.intellij.platform.searchEverywhere.SeItemsProvider
import com.intellij.platform.searchEverywhere.SeParams
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

class SeProviderInitializationGuardTest {
  @Test
  fun `waits for the provider after one slow opening`() = runGuardTest { guard ->
    guard.open(1) { slowProvider() }
    val provider = TestProvider()

    val result = guard.open(2) { slowProvider(provider) }

    assertSame(provider, result)
  }

  @Test
  fun `counts several calls of one opening once`() = runGuardTest { guard ->
    guard.open(1) { slowProvider() }
    guard.open(1) { slowProvider() }
    val provider = TestProvider()

    val result = guard.open(2) { slowProvider(provider) }

    assertSame(provider, result)
  }

  @Test
  fun `resets the count after a fast opening`() = runGuardTest { guard ->
    guard.open(1) { slowProvider() }
    guard.open(2) { TestProvider() }
    guard.open(3) { slowProvider() }
    val provider = TestProvider()

    val result = guard.open(4) { slowProvider(provider) }

    assertSame(provider, result)
  }

  @Test
  fun `skips the provider in the opening that opens the breaker`() = runGuardTest { guard ->
    guard.open(1) { slowProvider() }
    guard.open(2) { slowProvider() }
    val calls = AtomicInteger()

    val result = guard.open(2) { calls.incrementAndGet(); TestProvider() }

    assertNull(result)
    assertEquals(0, calls.get())
  }

  @Test
  fun `does not wait for a trial initialization`() = runGuardTest { guard ->
    guard.openBreaker()
    val trialProvider = TestProvider()

    val start = TimeSource.Monotonic.markNow()
    val result = guard.open(3) { slowProvider(trialProvider) }

    assertNull(result)
    assertTrue(start.elapsedNow() < SLOW_THRESHOLD, "The guard waited ${start.elapsedNow()}")
    trialProvider.awaitDisposed()
  }

  @Test
  fun `closes the breaker after a fast trial`() = runGuardTest { guard ->
    guard.openBreaker()
    val trialProvider = TestProvider()
    guard.open(3) { trialProvider }
    trialProvider.awaitDisposed()
    val provider = TestProvider()

    val result = guard.open(4) { provider }

    assertSame(provider, result)
  }

  @Test
  fun `doubles the cooldown after a slow trial`() = runGuardTest { guard ->
    guard.openBreaker()
    val trialProvider = TestProvider()
    guard.open(3) { slowProvider(trialProvider) }
    trialProvider.awaitDisposed()
    val calls = AtomicInteger()

    assertNull(guard.open(4) { calls.incrementAndGet(); TestProvider() })
    assertEquals(0, calls.get())

    val nextTrialProvider = TestProvider()
    assertNull(guard.open(5) { calls.incrementAndGet(); nextTrialProvider })
    nextTrialProvider.awaitDisposed()
    assertEquals(1, calls.get())
  }

  @Test
  fun `limits the cooldown to the maximum`() = runGuardTest(maxCooldownOpenings = 2) { guard ->
    guard.openBreaker()
    for (trialOpening in listOf(3, 5)) {
      val trialProvider = TestProvider()
      guard.open(trialOpening) { slowProvider(trialProvider) }
      trialProvider.awaitDisposed()
      guard.open(trialOpening + 1) { error("The breaker must skip opening ${trialOpening + 1}") }
    }
    val trialProvider = TestProvider()

    guard.open(7) { trialProvider }

    trialProvider.awaitDisposed()
  }

  private fun runGuardTest(
    maxCooldownOpenings: Int = SeProviderInitializationGuard.DEFAULT_MAX_COOLDOWN_OPENINGS,
    test: suspend CoroutineScope.(SeProviderInitializationGuard) -> Unit,
  ) = runBlocking {
    test(SeProviderInitializationGuard(this, SLOW_THRESHOLD, SeProviderInitializationGuard.DEFAULT_SLOW_OPENINGS_TO_OPEN,
                                       maxCooldownOpenings))
  }

  private suspend fun SeProviderInitializationGuard.open(opening: Int, initializer: suspend () -> SeItemsProvider?): SeItemsProvider? =
    initialize(FACTORY_ID, opening, null, LOG_LABEL, initializer)

  /**
   * Opens the breaker with the slow openings 1 and 2. The next opening runs a trial.
   */
  private suspend fun SeProviderInitializationGuard.openBreaker() {
    open(1) { slowProvider() }
    open(2) { slowProvider() }
  }

  private suspend fun slowProvider(provider: TestProvider = TestProvider()): TestProvider {
    delay(SLOW_THRESHOLD * 2)
    return provider
  }

  private class TestProvider : SeItemsProvider {
    private val disposed = CompletableDeferred<Unit>()

    override val id: String get() = FACTORY_ID
    override val displayName: String get() = FACTORY_ID

    override suspend fun collectItems(params: SeParams, collector: SeItemsProvider.Collector) {}
    override suspend fun itemSelected(item: SeItem, modifiers: Int, searchText: String): Boolean = false
    override suspend fun canBeShownInFindResults(): Boolean = false

    suspend fun awaitDisposed() {
      withTimeout(5.seconds) { disposed.await() }
    }

    override fun dispose() {
      disposed.complete(Unit)
    }
  }

  private companion object {
    const val FACTORY_ID = "TestFactory"
    const val LOG_LABEL = "Test"
    val SLOW_THRESHOLD = 100.milliseconds
  }
}
