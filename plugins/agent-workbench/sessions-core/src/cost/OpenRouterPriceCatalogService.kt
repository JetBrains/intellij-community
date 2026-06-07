// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.core.cost

import tools.jackson.core.JsonParser
import tools.jackson.core.JsonToken
import tools.jackson.core.ObjectReadContext
import tools.jackson.core.json.JsonFactory
import com.intellij.agent.workbench.common.session.AgentSessionCost
import com.intellij.agent.workbench.common.session.AgentSessionCostKind
import com.intellij.openapi.components.SerializablePersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.diagnostic.logger
import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.concurrent.atomic.AtomicBoolean

private val LOG = logger<OpenRouterPriceCatalogService>()

@Service(Service.Level.APP)
@State(name = "OpenRouterPriceCatalogService", storages = [Storage("agentWorkbenchOpenRouterPriceCatalog.xml")])
class OpenRouterPriceCatalogService(
  private val fetchSnapshot: () -> OpenRouterPriceSnapshot,
) : SerializablePersistentStateComponent<OpenRouterPriceCatalogService.CatalogState>(CatalogState()) {
  private val startupRefreshDone = AtomicBoolean(false)

  constructor() : this(fetchSnapshot = { OpenRouterPriceCatalogParser.fetchSnapshot() })

  fun refreshAtStartup() {
    if (!startupRefreshDone.compareAndSet(false, true)) return

    runCatching { fetchSnapshot() }
      .onSuccess { snapshot ->
        updateState { current -> current.copy(snapshot = snapshot) }
      }
      .onFailure { error ->
        LOG.warn("Failed to refresh OpenRouter price catalog at startup", error)
      }
  }

  fun currentSnapshot(): OpenRouterPriceSnapshot? = state.snapshot

  fun calculateCost(usage: AgentSessionUsageSnapshot): AgentSessionCost {
    usage.nativeExactCostUsd?.let { exactCost ->
      return AgentSessionCost(amountUsd = exactCost, kind = AgentSessionCostKind.EXACT)
    }

    val snapshot = state.snapshot ?: return AgentSessionCost(amountUsd = null, kind = AgentSessionCostKind.UNAVAILABLE)
    val matchedEntry = matchOpenRouterModel(usage.modelId, snapshot)
                       ?: return AgentSessionCost(amountUsd = null, kind = AgentSessionCostKind.UNAVAILABLE)
    val estimatedCost = matchedEntry.calculateCost(usage)
                        ?: return AgentSessionCost(amountUsd = null, kind = AgentSessionCostKind.UNAVAILABLE)

    return AgentSessionCost(
      amountUsd = estimatedCost,
      kind = AgentSessionCostKind.ESTIMATED,
      matchedModelId = matchedEntry.id,
    )
  }

  @Serializable
  data class CatalogState(
    @JvmField val snapshot: OpenRouterPriceSnapshot? = null,
  )
}

object OpenRouterPriceCatalogParser {
  private val jsonFactory = JsonFactory()

  private const val MODELS_URL = "https://openrouter.ai/api/v1/models"

  fun parseResponseBody(body: String, fetchedAt: Long = System.currentTimeMillis()): OpenRouterPriceSnapshot {
    val entries = mutableListOf<OpenRouterPriceEntry>()

    jsonFactory.createParser(ObjectReadContext.empty(), body).use { parser ->
      if (parser.nextToken() != JsonToken.START_OBJECT) {
        return OpenRouterPriceSnapshot(fetchedAt = fetchedAt)
      }

      while (parser.nextToken() != JsonToken.END_OBJECT) {
        val fieldName = parser.currentName()
        parser.nextToken()
        if (fieldName == "data" && parser.currentToken() == JsonToken.START_ARRAY) {
          while (parser.nextToken() != JsonToken.END_ARRAY) {
            parseEntry(parser)?.let(entries::add)
          }
        }
        else {
          parser.skipChildren()
        }
      }
    }

    return OpenRouterPriceSnapshot(fetchedAt = fetchedAt, entries = entries)
  }

  fun fetchSnapshot(httpClient: HttpClient = HttpClient.newHttpClient()): OpenRouterPriceSnapshot {
    val request = HttpRequest.newBuilder(URI.create(MODELS_URL))
      .header("Accept", "application/json")
      .GET()
      .build()
    val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
    check(response.statusCode() in 200..299) { "OpenRouter pricing request failed with status ${response.statusCode()}" }
    return parseResponseBody(response.body())
  }

  private fun parseEntry(parser: JsonParser): OpenRouterPriceEntry? {
    if (parser.currentToken() != JsonToken.START_OBJECT) {
      parser.skipChildren()
      return null
    }

    var id: String? = null
    var canonicalSlug: String? = null
    var displayName: String? = null
    var promptTokenPriceUsd: String? = null
    var completionTokenPriceUsd: String? = null
    var cacheReadTokenPriceUsd: String? = null
    var cacheWriteTokenPriceUsd: String? = null

    while (parser.nextToken() != JsonToken.END_OBJECT) {
      val fieldName = parser.currentName()
      parser.nextToken()
      when (fieldName) {
        "id" -> id = parser.readStringOrNull()
        "canonical_slug" -> canonicalSlug = parser.readStringOrNull()
        "name" -> displayName = parser.readStringOrNull()
        "pricing" -> {
          if (parser.currentToken() == JsonToken.START_OBJECT) {
            while (parser.nextToken() != JsonToken.END_OBJECT) {
              val pricingField = parser.currentName()
              parser.nextToken()
              when (pricingField) {
                "prompt" -> promptTokenPriceUsd = parser.readStringOrNull()
                "completion" -> completionTokenPriceUsd = parser.readStringOrNull()
                "input_cache_read" -> cacheReadTokenPriceUsd = parser.readStringOrNull()
                "input_cache_write" -> cacheWriteTokenPriceUsd = parser.readStringOrNull()
                else -> parser.skipChildren()
              }
            }
          }
          else {
            parser.skipChildren()
          }
        }
        else -> parser.skipChildren()
      }
    }

    val resolvedId = id ?: return null
    return createOpenRouterPriceEntry(
      id = resolvedId,
      canonicalSlug = canonicalSlug,
      displayName = displayName,
      promptTokenPriceUsd = promptTokenPriceUsd,
      completionTokenPriceUsd = completionTokenPriceUsd,
      cacheReadTokenPriceUsd = cacheReadTokenPriceUsd,
      cacheWriteTokenPriceUsd = cacheWriteTokenPriceUsd,
    )
  }
}

private fun JsonParser.readStringOrNull(): String? {
  return when (currentToken()) {
    JsonToken.VALUE_STRING -> string
    JsonToken.VALUE_NUMBER_FLOAT,
    JsonToken.VALUE_NUMBER_INT,
      -> numberValue.toString()
    JsonToken.VALUE_NULL -> null
    else -> {
      skipChildren()
      null
    }
  }
}

private fun OpenRouterPriceEntry.calculateCost(usage: AgentSessionUsageSnapshot): BigDecimal? {
  val inputCost = usage.inputTokens.costContribution(promptTokenPriceUsd) ?: return null
  val outputCost = usage.outputTokens.costContribution(completionTokenPriceUsd) ?: return null
  val cacheReadCost = usage.cacheReadTokens.costContribution(cacheReadTokenPriceUsd) ?: return null
  val cacheWriteCost = usage.cacheWriteTokens.costContribution(cacheWriteTokenPriceUsd) ?: return null
  return inputCost + outputCost + cacheReadCost + cacheWriteCost
}

private fun Long.costContribution(pricePerTokenUsd: String?): BigDecimal? {
  if (this == 0L) return BigDecimal.ZERO
  val price = pricePerTokenUsd?.let(::BigDecimal) ?: return null
  return price.multiply(BigDecimal.valueOf(this))
}
