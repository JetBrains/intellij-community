// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.cost

import com.intellij.agent.workbench.core.session.AgentSessionCost
import com.intellij.agent.workbench.core.session.AgentSessionCostKind
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Clock
import java.util.concurrent.atomic.AtomicBoolean

private val LOG = logger<LiteLlmPriceCatalogService>()

@Service(Service.Level.APP)
class LiteLlmPriceCatalogService(
  private val configDirProvider: () -> java.nio.file.Path? = { PathManager.getConfigDir() },
  private val fetchPricingJson: () -> String = { defaultFetchPricingJson() },
  private val clock: Clock = Clock.systemUTC(),
) {
  private val startupRefreshDone = AtomicBoolean(false)

  @Volatile
  private var snapshot: LiteLlmPricingCatalogSnapshot = LiteLlmPriceCatalog.embeddedSnapshot()

  fun refreshAtStartup() {
    if (!startupRefreshDone.compareAndSet(false, true)) return

    runCatching {
      LiteLlmPriceCatalog.resolveCatalog(
        configDir = configDirProvider(),
        clock = clock,
        refreshMissingCache = true,
        fetchPricingJson = fetchPricingJson,
      )
    }
      .onSuccess { refreshed -> snapshot = refreshed }
      .onFailure { error -> LOG.warn("Failed to refresh LiteLLM price catalog at startup", error) }
  }

  fun currentSnapshot(): LiteLlmPricingCatalogSnapshot = snapshot

  fun calculateCost(usage: AgentSessionUsageSnapshot): AgentSessionCost {
    usage.nativeExactCostUsd?.let { exactCost ->
      return AgentSessionCost(amountUsd = exactCost, kind = AgentSessionCostKind.EXACT)
    }

    val matchedEntry = LiteLlmPriceCatalog.matchEntry(usage.modelId, snapshot)
                      ?: return AgentSessionCost(amountUsd = null, kind = AgentSessionCostKind.UNAVAILABLE)
    val amountUsd = runCatching { LiteLlmPriceCatalog.calculateCost(matchedEntry, usage) }.getOrNull()
                    ?: return AgentSessionCost(amountUsd = null, kind = AgentSessionCostKind.UNAVAILABLE)
    return AgentSessionCost(
      amountUsd = amountUsd,
      kind = AgentSessionCostKind.ESTIMATED,
      matchedModelId = LiteLlmPriceCatalog.primaryAlias(matchedEntry),
    )
  }

  companion object {
    internal fun defaultFetchPricingJson(httpClient: HttpClient = HttpClient.newBuilder().build()): String {
      val request = HttpRequest.newBuilder(URI.create("https://raw.githubusercontent.com/BerriAI/litellm/main/model_prices_and_context_window.json"))
        .header("Accept", "application/json")
        .GET()
        .build()
      val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
      check(response.statusCode() in 200..299) { "LiteLLM pricing request failed with status ${response.statusCode()}" }
      return response.body()
    }
  }
}
