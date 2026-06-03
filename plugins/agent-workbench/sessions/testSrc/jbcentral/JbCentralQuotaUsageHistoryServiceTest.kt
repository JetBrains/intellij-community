// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.jbcentral

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class JbCentralQuotaUsageHistoryServiceTest {
  private val dayMs = 24L * 60 * 60 * 1000

  @Test
  fun recordsDistinctSamplesInOrder() {
    val service = JbCentralQuotaUsageHistoryService()
    service.record(100.0, nowMs = 1_000)
    service.record(250.0, nowMs = 2_000)

    val samples = service.samples()
    assertThat(samples.map { it.usedUsd }).containsExactly(100.0, 250.0)
    assertThat(samples.map { it.timestampMs }).containsExactly(1_000L, 2_000L)
  }

  @Test
  fun skipsTinyDeltaWithinGap() {
    val service = JbCentralQuotaUsageHistoryService()
    service.record(100.0, nowMs = 1_000)
    service.record(100.002, nowMs = 1_000 + 1_000) // delta < $0.005 and < 240s since last

    assertThat(service.samples()).hasSize(1)
  }

  @Test
  fun recordsWhenDeltaExceedsThresholdWithinGap() {
    val service = JbCentralQuotaUsageHistoryService()
    service.record(100.0, nowMs = 1_000)
    service.record(100.01, nowMs = 1_000 + 1_000) // delta >= $0.005

    assertThat(service.samples()).hasSize(2)
  }

  @Test
  fun recordsTinyDeltaWhenGapExceedsThreshold() {
    val service = JbCentralQuotaUsageHistoryService()
    service.record(100.0, nowMs = 1_000)
    service.record(100.001, nowMs = 1_000 + 240_001) // tiny delta, but gap >= 240s

    assertThat(service.samples()).hasSize(2)
  }

  @Test
  fun prunesSamplesOlderThanNinetyDays() {
    val service = JbCentralQuotaUsageHistoryService()
    val t0 = 1_000_000L
    service.record(100.0, nowMs = t0)
    service.record(200.0, nowMs = t0 + 91 * dayMs) // 91 days later -> first sample pruned

    val samples = service.samples()
    assertThat(samples).hasSize(1)
    assertThat(samples.single().usedUsd).isEqualTo(200.0)
  }
}
