// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.junie.sessions

import tools.jackson.core.JsonParser
import tools.jackson.core.JsonToken
import tools.jackson.core.json.JsonFactory
import com.intellij.agent.workbench.common.session.AgentSessionCost
import com.intellij.agent.workbench.common.session.AgentSessionCostKind
import com.intellij.agent.workbench.json.WorkbenchJsonlScanner
import com.intellij.agent.workbench.json.forEachJsonObjectField
import com.intellij.agent.workbench.json.readJsonStringOrNull
import com.intellij.openapi.diagnostic.logger
import com.intellij.util.SystemProperties
import java.math.BigDecimal
import java.nio.file.Files
import java.nio.file.Path

private val LOG = logger<JunieSessionCostLoader>()

internal class JunieSessionCostLoader(
  private val sessionsRootPathProvider: () -> Path = ::defaultJunieSessionsRootPath,
  private val jsonFactory: JsonFactory = JsonFactory(),
) {
  fun loadCost(sessionId: String): AgentSessionCost? {
    val eventsPath = sessionsRootPathProvider().resolve(sessionId).resolve("events.jsonl")
    if (!Files.isRegularFile(eventsPath)) return null

    return try {
      var totalCost = BigDecimal.ZERO
      var sawKnownCost = false
      var sawMissingCost = false

      WorkbenchJsonlScanner.scanJsonObjects(
        path = eventsPath,
        jsonFactory = jsonFactory,
        newState = {},
      ) { parser, _ ->
        collectRootModelUsageCosts(parser) { cost ->
          if (cost == null) {
            sawMissingCost = true
          }
          else {
            totalCost += cost
            sawKnownCost = true
          }
        }
        true
      }

      if (!sawKnownCost) {
        null
      }
      else {
        AgentSessionCost(
          amountUsd = totalCost,
          kind = if (sawMissingCost) AgentSessionCostKind.ESTIMATED else AgentSessionCostKind.EXACT,
        )
      }
    }
    catch (e: Exception) {
      LOG.debug("Failed to load Junie session cost from $eventsPath", e)
      null
    }
  }
}

private fun collectRootModelUsageCosts(parser: JsonParser, consume: (BigDecimal?) -> Unit) {
  if (parser.currentToken() != JsonToken.START_OBJECT) {
    parser.skipChildren()
    return
  }

  var rootKind: String? = null
  val collected = ArrayList<BigDecimal?>()
  forEachJsonObjectField(parser) { fieldName ->
    when (fieldName) {
      "kind" -> rootKind = readJsonStringOrNull(parser)
      "event" -> collectSessionA2uxCosts(parser, collected)
      else -> parser.skipChildren()
    }
    true
  }
  if (rootKind == "SessionA2uxEvent") {
    collected.forEach(consume)
  }
}

private fun collectSessionA2uxCosts(parser: JsonParser, collector: MutableList<BigDecimal?>) {
  if (parser.currentToken() != JsonToken.START_OBJECT) {
    parser.skipChildren()
    return
  }

  forEachJsonObjectField(parser) { fieldName ->
    when (fieldName) {
      "agentEvent" -> collectAgentEventCosts(parser, collector)
      else -> parser.skipChildren()
    }
    true
  }
}

private fun collectAgentEventCosts(parser: JsonParser, collector: MutableList<BigDecimal?>) {
  if (parser.currentToken() != JsonToken.START_OBJECT) {
    parser.skipChildren()
    return
  }

  var agentEventKind: String? = null
  var modelUsageCosts: List<BigDecimal?> = emptyList()
  forEachJsonObjectField(parser) { fieldName ->
    when (fieldName) {
      "kind" -> agentEventKind = readJsonStringOrNull(parser)
      "modelUsage" -> modelUsageCosts = readModelUsageCosts(parser)
      else -> parser.skipChildren()
    }
    true
  }
  if (agentEventKind == "LlmResponseMetadataEvent") {
    collector += modelUsageCosts
  }
}

private fun readModelUsageCosts(parser: JsonParser): List<BigDecimal?> {
  if (parser.currentToken() != JsonToken.START_ARRAY) {
    parser.skipChildren()
    return emptyList()
  }

  val result = ArrayList<BigDecimal?>()
  while (parser.nextToken() != JsonToken.END_ARRAY) {
    result += readModelUsageCost(parser)
  }
  return result
}

private fun readModelUsageCost(parser: JsonParser): BigDecimal? {
  if (parser.currentToken() != JsonToken.START_OBJECT) {
    parser.skipChildren()
    return null
  }

  var cost: BigDecimal? = null
  forEachJsonObjectField(parser) { fieldName ->
    when (fieldName) {
      "cost" -> cost = readJsonBigDecimalOrNull(parser)
      else -> parser.skipChildren()
    }
    true
  }
  return cost
}

private fun readJsonBigDecimalOrNull(parser: JsonParser): BigDecimal? {
  return when (parser.currentToken()) {
    JsonToken.VALUE_NUMBER_FLOAT, JsonToken.VALUE_NUMBER_INT -> parser.decimalValue
    JsonToken.VALUE_STRING -> parser.string.toBigDecimalOrNull()
    JsonToken.VALUE_NULL -> null
    else -> {
      parser.skipChildren()
      null
    }
  }
}

internal fun defaultJunieSessionsRootPath(): Path {
  return Path.of(SystemProperties.getUserHome(), ".junie", "sessions")
}
