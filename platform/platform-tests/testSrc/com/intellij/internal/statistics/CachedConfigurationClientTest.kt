// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistics

import com.intellij.internal.statistic.eventLog.connection.CachedConfigurationClient
import com.jetbrains.fus.reporting.configuration.ConfigurationClient
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.util.concurrent.TimeUnit
import kotlin.system.measureTimeMillis

class CachedConfigurationClientTest {
  /**
   * cacheTimeoutMs = 0 makes the cache always considered expired, so every call triggers a refresh.
   */
  private val alwaysExpired = 0L
  private val delegate = mockk<ConfigurationClient>(relaxed = true)

  @Test
  fun `update is retried up to maxUpdateAttempts when it keeps failing`() {
    every { delegate.update() } returns false

    val client = CachedConfigurationClient(delegate, cacheTimeoutMs = alwaysExpired, maxUpdateAttempts = 3, updateRetryDelayMs = 0)
    client.isConfigurationReachable()

    verify(exactly = 3) { delegate.update() }
  }

  @Test
  fun `update is not retried after the first success`() {
    every { delegate.update() } returns true

    val client = CachedConfigurationClient(delegate, cacheTimeoutMs = alwaysExpired, maxUpdateAttempts = 3, updateRetryDelayMs = 0)
    client.isConfigurationReachable()

    verify(exactly = 1) { delegate.update() }
  }

  @Test
  fun `update retries until it succeeds and then stops`() {
    every { delegate.update() } returnsMany listOf(false, false, true)

    val client = CachedConfigurationClient(delegate, cacheTimeoutMs = alwaysExpired, maxUpdateAttempts = 5, updateRetryDelayMs = 0)
    client.isConfigurationReachable()

    verify(exactly = 3) { delegate.update() }
  }

  @Test
  fun `delay is applied between failing retries`() {
    every { delegate.update() } returns false

    val delayMs = 50L
    val client = CachedConfigurationClient(delegate, cacheTimeoutMs = alwaysExpired, maxUpdateAttempts = 3, updateRetryDelayMs = delayMs)

    val elapsed = measureTimeMillis { client.isConfigurationReachable() }

    // 3 attempts, 2 inter-attempt delays; the delay is not applied after the final failed attempt.
    verify(exactly = 3) { delegate.update() }
    assertThat(elapsed).isGreaterThanOrEqualTo(2 * delayMs)
  }

  @Test
  fun `update is not called again while the cache is still valid`() {
    every { delegate.update() } returns true

    val client = CachedConfigurationClient(delegate, cacheTimeoutMs = TimeUnit.HOURS.toMillis(1), maxUpdateAttempts = 3, updateRetryDelayMs = 0)
    client.isConfigurationReachable()
    client.isConfigurationReachable()

    verify(exactly = 1) { delegate.update() }
  }
}