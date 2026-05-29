// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.core.cost

import com.intellij.agent.workbench.common.session.AgentSessionCostKind
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.concurrent.atomic.AtomicInteger

class OpenRouterPriceCatalogServiceTest {
  @Test
  fun refreshAtStartupLoadsSnapshotOnce() {
    val fetchCount = AtomicInteger()
    val service = OpenRouterPriceCatalogService(
      fetchSnapshot = {
        fetchCount.incrementAndGet()
        OpenRouterPriceCatalogParser.parseResponseBody(loadFixture(), fetchedAt = 42L)
      },
    )

    service.refreshAtStartup()
    service.refreshAtStartup()

    assertThat(fetchCount.get()).isEqualTo(1)
    assertThat(service.currentSnapshot()).isNotNull
    assertThat(service.currentSnapshot()!!.entries).isNotEmpty
  }

  @Test
  fun refreshAtStartupKeepsPersistedSnapshotWhenFetchFails() {
    val persistedSnapshot = OpenRouterPriceSnapshot(
      fetchedAt = 7L,
      entries = listOf(
        OpenRouterPriceEntry(
          id = "openai/gpt-chat-latest",
          canonicalSlug = "openai/gpt-chat-latest-20260505",
          displayName = "OpenAI: GPT Chat Latest",
          normalizedNames = setOf("openai-gpt-chat-latest", "gpt-chat-latest", "openai-gpt-chat-latest-20260505", "gpt-chat-latest-20260505"),
          promptTokenPriceUsd = "0.000005",
          completionTokenPriceUsd = "0.00003",
          cacheReadTokenPriceUsd = "0.0000005",
          cacheWriteTokenPriceUsd = null,
        ),
      ),
    )
    val service = OpenRouterPriceCatalogService(fetchSnapshot = { error("offline") })
    service.loadState(OpenRouterPriceCatalogService.CatalogState(snapshot = persistedSnapshot))

    service.refreshAtStartup()

    assertThat(service.currentSnapshot()).isEqualTo(persistedSnapshot)
  }

  @Test
  fun calculatesEstimatedCostFromSnapshotAndPrefersExactNativeCost() {
    val snapshot = OpenRouterPriceCatalogParser.parseResponseBody(loadFixture(), fetchedAt = 42L)
    val service = OpenRouterPriceCatalogService(fetchSnapshot = { snapshot })
    service.loadState(OpenRouterPriceCatalogService.CatalogState(snapshot = snapshot))

    val estimated = service.calculateCost(
      AgentSessionUsageSnapshot(
        modelId = "openai/gpt-chat-latest-20260505",
        inputTokens = 2,
        outputTokens = 3,
        cacheReadTokens = 5,
      ),
    )

    assertThat(estimated.kind).isEqualTo(AgentSessionCostKind.ESTIMATED)
    assertThat(estimated.amountUsd).isEqualByComparingTo(BigDecimal("0.0001025"))

    val exact = service.calculateCost(
      AgentSessionUsageSnapshot(
        modelId = "openai/gpt-chat-latest-20260505",
        inputTokens = 2,
        outputTokens = 3,
        nativeExactCostUsd = BigDecimal("1.23"),
      ),
    )

    assertThat(exact.kind).isEqualTo(AgentSessionCostKind.EXACT)
    assertThat(exact.amountUsd).isEqualByComparingTo(BigDecimal("1.23"))
  }

  private fun loadFixture(): String {
    val resourcePath = "openrouter/models-snapshot.json"
    return checkNotNull(javaClass.classLoader.getResource(resourcePath)) {
      "Missing fixture resource: $resourcePath"
    }.readText()
  }
}
