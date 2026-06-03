// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.jbcentral

import com.intellij.openapi.components.SerializablePersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import kotlinx.serialization.Serializable
import kotlin.math.abs

private const val MIN_GAP_MS = 240_000L
private const val MIN_DELTA_USD = 0.005
private const val RETENTION_DAYS = 90

@Service(Service.Level.APP)
@State(name = "JbCentralQuotaUsageHistory", storages = [Storage(StoragePathMacros.CACHE_FILE)])
internal class JbCentralQuotaUsageHistoryService : SerializablePersistentStateComponent<JbCentralQuotaUsageHistoryService.State>(State()) {
  fun samples(): List<UsageSample> = state.samples

  fun record(usedUsd: Double, nowMs: Long = System.currentTimeMillis()) {
    val last = state.samples.lastOrNull()
    if (last != null && abs(usedUsd - last.usedUsd) < MIN_DELTA_USD && (nowMs - last.timestampMs) < MIN_GAP_MS) {
      return
    }
    val cutoff = nowMs - RETENTION_DAYS.toLong() * 24 * 60 * 60 * 1000
    updateState { current ->
      val samples = (current.samples + UsageSample(nowMs, usedUsd))
        .filter { it.timestampMs >= cutoff }
        .sortedBy { it.timestampMs }
      current.copy(samples = samples)
    }
  }

  @Serializable
  data class State(
    @JvmField val samples: List<UsageSample> = emptyList(),
  )
}
