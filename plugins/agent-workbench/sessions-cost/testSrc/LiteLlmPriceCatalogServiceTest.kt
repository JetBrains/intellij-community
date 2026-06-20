// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.cost

import com.intellij.agent.workbench.core.session.AgentSessionCostKind
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.math.BigDecimal
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicInteger

class LiteLlmPriceCatalogServiceTest {
  @TempDir
  lateinit var tempDir: Path

  @Test
  fun refreshAtStartupLoadsCatalogOnce() {
    val fetchCount = AtomicInteger()
    val service = LiteLlmPriceCatalogService(
      configDirProvider = { tempDir },
      fetchPricingJson = {
        fetchCount.incrementAndGet()
        minimalCatalogJson()
      },
      clock = fixedClock(),
    )

    service.refreshAtStartup()
    service.refreshAtStartup()

    assertThat(fetchCount.get()).isEqualTo(1)
    assertThat(service.currentSnapshot().aliasEntries + service.currentSnapshot().directEntries).isNotEmpty()
  }

  @Test
  fun resolveCatalogPrefersFreshCachedSnapshot() {
    val cachePath = LiteLlmPriceCatalog.cachePath(tempDir)
    LiteLlmPriceCatalog.writeCachedSnapshot(
      cachePath = cachePath,
      snapshot = LiteLlmCachedPricingSnapshot(
        fetchedAt = "2026-06-16T08:00:00Z",
        body = """
          {
            "gpt-5.4": {
              "input_cost_per_token": 0.0000025,
              "output_cost_per_token": 0.000015
            }
          }
        """.trimIndent(),
      ),
    )

    val snapshot = LiteLlmPriceCatalog.resolveCatalog(
      configDir = tempDir,
      clock = fixedClock(),
      refreshMissingCache = true,
      fetchPricingJson = { error("fresh cache should be reused") },
    )

    val entry = LiteLlmPriceCatalog.matchEntry("gpt-5.4", snapshot)
    assertThat(entry?.promptTokenPriceUsd).isEqualByComparingTo(BigDecimal("0.0000025"))
    assertThat(entry?.cacheWriteTokenPriceUsd).isEqualByComparingTo(BigDecimal("0.000003125"))
  }

  @Test
  fun calculatesEstimatedCostFromLiteLlmSnapshotAndPrefersExactNativeCost() {
    val service = LiteLlmPriceCatalogService(
      configDirProvider = { null },
      fetchPricingJson = { minimalCatalogJson() },
      clock = fixedClock(),
    )
    service.refreshAtStartup()

    val estimated = service.calculateCost(
      AgentSessionUsageSnapshot(
        modelId = "openai/gpt-5.4-20260305",
        inputTokens = 2,
        outputTokens = 3,
        cacheReadTokens = 5,
        cacheWrite5mTokens = 7,
        cacheWrite1hTokens = 11,
      ),
    )

    assertThat(estimated.kind).isEqualTo(AgentSessionCostKind.ESTIMATED)
    assertThat(estimated.amountUsd).isEqualByComparingTo(BigDecimal("0.000128125"))

    val exact = service.calculateCost(
      AgentSessionUsageSnapshot(
        modelId = "openai/gpt-5.4-20260305",
        inputTokens = 2,
        outputTokens = 3,
        nativeExactCostUsd = BigDecimal("1.23"),
      ),
    )

    assertThat(exact.kind).isEqualTo(AgentSessionCostKind.EXACT)
    assertThat(exact.amountUsd).isEqualByComparingTo(BigDecimal("1.23"))
  }

  private fun fixedClock(): Clock = Clock.fixed(Instant.parse("2026-06-16T10:15:30Z"), ZoneOffset.UTC)

  private fun minimalCatalogJson(): String {
    return """
      {
        "gpt-5.4": {
          "input_cost_per_token": 0.0000025,
          "output_cost_per_token": 0.000015
        },
        "claude-sonnet-4-6": {
          "input_cost_per_token": 0.000003,
          "output_cost_per_token": 0.000015,
          "cache_creation_input_token_cost": 0.00000375,
          "cache_read_input_token_cost": 0.0000003
        }
      }
    """.trimIndent()
  }
}
