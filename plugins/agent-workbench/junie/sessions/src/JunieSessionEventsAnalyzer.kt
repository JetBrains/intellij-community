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
import java.math.BigDecimal
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

private val EVENTS_LOG = logger<JunieSessionEventsAnalyzer>()

internal class JunieSessionEventsAnalyzer(
  private val sessionsRootPathProvider: () -> Path = ::defaultJunieSessionsRootPath,
  private val jsonFactory: JsonFactory = JsonFactory(),
) {
  private val analysisCache = ConcurrentHashMap<String, CachedEventsAnalysis>()
  private val costCache = ConcurrentHashMap<CostCacheKey, CachedCost>()

  fun cachedAnalysis(sessionId: String): JunieSessionEventsAnalysis? {
    return analysisCache[sessionId]?.analysis
  }

  fun loadAnalysis(sessionId: String): JunieSessionEventsAnalysis? {
    val eventsPath = eventsPath(sessionId)
    val stat = readEventsFileStat(eventsPath) ?: return null
    val cached = analysisCache[sessionId]
    if (cached != null && cached.stat == stat) {
      return cached.analysis
    }

    val analysis = scanEvents(eventsPath) ?: return null
    analysisCache[sessionId] = CachedEventsAnalysis(stat = stat, analysis = analysis)
    return analysis
  }

  fun loadCost(sessionId: String): AgentSessionCost? {
    return loadAnalysis(sessionId)?.cost
  }

  fun loadCost(sessionId: String, updatedAt: Long): AgentSessionCost? {
    val key = CostCacheKey(sessionId = sessionId, updatedAt = updatedAt)
    val cached = costCache[key]
    if (cached != null) {
      return cached.cost
    }

    val cost = loadAnalysis(sessionId)?.cost
    costCache[key] = CachedCost(cost)
    return cost
  }

  private fun eventsPath(sessionId: String): Path {
    return sessionsRootPathProvider().resolve(sessionId).resolve(JUNIE_EVENTS_FILE_NAME)
  }

  private fun scanEvents(eventsPath: Path): JunieSessionEventsAnalysis? {
    if (!Files.isRegularFile(eventsPath)) return null
    return try {
      val state = WorkbenchJsonlScanner.scanJsonObjects(
        path = eventsPath,
        jsonFactory = jsonFactory,
        newState = { JunieEventsParseState() },
      ) { parser, parseState ->
        parseState.reduceRootEvent(parser)
        true
      }
      state.toAnalysis()
    }
    catch (e: Exception) {
      EVENTS_LOG.debug("Failed to load Junie session events from $eventsPath", e)
      null
    }
  }
}

internal data class JunieSessionEventsAnalysis(
  @JvmField val cost: AgentSessionCost?,
  @JvmField val activity: JunieSessionEventsActivity?,
  @JvmField val changedProjectFilePaths: Set<String>?,
)

internal enum class JunieSessionEventsActivity {
  READY,
  PROCESSING,
}

internal const val JUNIE_EVENTS_FILE_NAME = "events.jsonl"

private class JunieEventsParseState {
  private var totalCost = BigDecimal.ZERO
  private var sawKnownCost = false
  private var sawMissingCost = false
  private var activity: JunieSessionEventsActivity? = null
  private val activeTerminalSteps = LinkedHashSet<String>()
  private val changedProjectFilePaths = LinkedHashSet<String>()

  fun reduceRootEvent(parser: JsonParser) {
    if (parser.currentToken() != JsonToken.START_OBJECT) {
      parser.skipChildren()
      return
    }

    var rootKind: String? = null
    var topLevelType: String? = null
    var sessionA2uxEvent: ParsedSessionA2uxEvent? = null
    var acpEvent: ParsedAcpEvent? = null
    forEachJsonObjectField(parser) { fieldName ->
      when (fieldName) {
        "kind" -> rootKind = readJsonStringOrNull(parser)
        "type" -> topLevelType = readJsonStringOrNull(parser)
        "event" -> sessionA2uxEvent = parseSessionA2uxEvent(parser)
        "data" -> acpEvent = parseAcpDataEvent(parser)
        else -> parser.skipChildren()
      }
      true
    }

    when (rootKind) {
      "SendToAgentEvent", "UserPromptEvent" -> activity = JunieSessionEventsActivity.PROCESSING
      "SessionA2uxEvent" -> sessionA2uxEvent?.let(::applySessionA2uxEvent)
    }

    if (normalizeToken(topLevelType) == "update") {
      acpEvent?.let(::applyAcpEvent)
    }
  }

  fun toAnalysis(): JunieSessionEventsAnalysis {
    val cost = if (!sawKnownCost) {
      null
    }
    else {
      AgentSessionCost(
        amountUsd = totalCost,
        kind = if (sawMissingCost) AgentSessionCostKind.ESTIMATED else AgentSessionCostKind.EXACT,
      )
    }
    val resolvedActivity = if (activeTerminalSteps.isNotEmpty()) JunieSessionEventsActivity.PROCESSING else activity
    return JunieSessionEventsAnalysis(
      cost = cost,
      activity = resolvedActivity,
      changedProjectFilePaths = changedProjectFilePaths.takeIf { it.isNotEmpty() },
    )
  }

  private fun applySessionA2uxEvent(event: ParsedSessionA2uxEvent) {
    collectCosts(event.modelUsageCosts)
    changedProjectFilePaths.addAll(event.changedProjectFilePaths)
    when (event.agentEventKind) {
      "AgentCurrentStatusUpdatedEvent" -> {
        activity = if (event.status.isNullOrBlank()) JunieSessionEventsActivity.READY else JunieSessionEventsActivity.PROCESSING
      }

      "TerminalBlockUpdatedEvent" -> reduceTerminalBlock(event)
    }
  }

  private fun applyAcpEvent(event: ParsedAcpEvent) {
    val updateKind = normalizeToken(event.sessionUpdate)
    val status = normalizeToken(event.status)
    when {
      updateKind == "prompt" -> activity = JunieSessionEventsActivity.PROCESSING
      updateKind == "agentmessagechunk" -> activity = JunieSessionEventsActivity.PROCESSING
      status in RUNNING_STATUS_TOKENS -> activity = JunieSessionEventsActivity.PROCESSING
      status in TERMINAL_STATUS_TOKENS -> activity = JunieSessionEventsActivity.READY
    }
  }

  private fun reduceTerminalBlock(event: ParsedSessionA2uxEvent) {
    val status = normalizeToken(event.status)
    val state = normalizeToken(event.state)
    val stepId = event.stepId?.takeIf { it.isNotBlank() }
    when {
      status in RUNNING_STATUS_TOKENS -> {
        activeTerminalSteps.add(stepId ?: UNKNOWN_TERMINAL_STEP_ID)
        activity = JunieSessionEventsActivity.PROCESSING
      }

      status in TERMINAL_STATUS_TOKENS -> {
        if (stepId == null) {
          activeTerminalSteps.remove(UNKNOWN_TERMINAL_STEP_ID)
        }
        else {
          activeTerminalSteps.remove(stepId)
        }
        activity = if (activeTerminalSteps.isEmpty()) JunieSessionEventsActivity.READY else JunieSessionEventsActivity.PROCESSING
      }

      state in RUNNING_STATUS_TOKENS -> {
        activeTerminalSteps.add(stepId ?: UNKNOWN_TERMINAL_STEP_ID)
        activity = JunieSessionEventsActivity.PROCESSING
      }
    }
  }

  private fun collectCosts(costs: List<BigDecimal?>) {
    for (cost in costs) {
      if (cost == null) {
        sawMissingCost = true
      }
      else {
        totalCost += cost
        sawKnownCost = true
      }
    }
  }
}

private fun parseSessionA2uxEvent(parser: JsonParser): ParsedSessionA2uxEvent? {
  if (parser.currentToken() != JsonToken.START_OBJECT) {
    parser.skipChildren()
    return null
  }

  var state: String? = null
  var agentEvent: ParsedAgentEvent? = null
  forEachJsonObjectField(parser) { fieldName ->
    when (fieldName) {
      "state" -> state = readJsonStringOrNull(parser)
      "agentEvent" -> agentEvent = parseAgentEvent(parser)
      else -> parser.skipChildren()
    }
    true
  }
  return ParsedSessionA2uxEvent(
    state = state,
    agentEventKind = agentEvent?.kind,
    status = agentEvent?.status,
    stepId = agentEvent?.stepId,
    modelUsageCosts = agentEvent?.modelUsageCosts.orEmpty(),
    changedProjectFilePaths = agentEvent?.changedProjectFilePaths.orEmpty(),
  )
}

private fun parseAgentEvent(parser: JsonParser): ParsedAgentEvent? {
  if (parser.currentToken() != JsonToken.START_OBJECT) {
    parser.skipChildren()
    return null
  }

  var kind: String? = null
  var status: String? = null
  var stepId: String? = null
  var modelUsageCosts: List<BigDecimal?> = emptyList()
  val changedProjectFilePaths = LinkedHashSet<String>()
  forEachJsonObjectField(parser) { fieldName ->
    when (fieldName) {
      "kind" -> kind = readJsonStringOrNull(parser)
      "status" -> status = readJsonStringOrNull(parser)
      "stepId", "stepID", "id" -> stepId = readJsonStringOrNull(parser)?.trim()?.takeIf { it.isNotEmpty() } ?: stepId
      "modelUsage" -> modelUsageCosts = readModelUsageCosts(parser)
      "changes" -> collectChangedProjectFilePaths(parser, changedProjectFilePaths)
      else -> parser.skipChildren()
    }
    true
  }
  return ParsedAgentEvent(
    kind = kind,
    status = status,
    stepId = stepId,
    modelUsageCosts = if (kind == "LlmResponseMetadataEvent") modelUsageCosts else emptyList(),
    changedProjectFilePaths = changedProjectFilePaths,
  )
}

private fun parseAcpDataEvent(parser: JsonParser): ParsedAcpEvent? {
  if (parser.currentToken() != JsonToken.START_OBJECT) {
    parser.skipChildren()
    return null
  }

  var sessionUpdate: String? = null
  var status: String? = null
  forEachJsonObjectField(parser) { fieldName ->
    when (fieldName) {
      "sessionUpdate" -> sessionUpdate = readJsonStringOrNull(parser)
      "status" -> status = readJsonStringOrNull(parser)
      else -> parser.skipChildren()
    }
    true
  }
  return ParsedAcpEvent(sessionUpdate = sessionUpdate, status = status)
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

private fun collectChangedProjectFilePaths(parser: JsonParser, result: MutableSet<String>) {
  if (parser.currentToken() != JsonToken.START_ARRAY) {
    parser.skipChildren()
    return
  }

  while (parser.nextToken() != JsonToken.END_ARRAY) {
    when (parser.currentToken()) {
      JsonToken.VALUE_STRING -> parser.string.trim().takeIf { it.isNotEmpty() }?.let(result::add)
      JsonToken.START_OBJECT -> collectChangedProjectFilePath(parser, result)
      else -> parser.skipChildren()
    }
  }
}

private fun collectChangedProjectFilePath(parser: JsonParser, result: MutableSet<String>) {
  forEachJsonObjectField(parser) { fieldName ->
    when (fieldName) {
      "path", "filePath", "relativePath" -> readJsonStringOrNull(parser)?.trim()?.takeIf { it.isNotEmpty() }?.let(result::add)
      else -> parser.skipChildren()
    }
    true
  }
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

private fun readEventsFileStat(path: Path): EventsFileStat? {
  return try {
    if (!Files.isRegularFile(path)) return null
    EventsFileStat(
      lastModifiedMillis = Files.getLastModifiedTime(path).toMillis(),
      size = Files.size(path),
    )
  }
  catch (_: Exception) {
    null
  }
}

private fun normalizeToken(value: String?): String {
  return value
    ?.trim()
    ?.lowercase()
    ?.replace("_", "")
    ?.replace("-", "")
    ?.replace(" ", "")
    .orEmpty()
}

private data class ParsedSessionA2uxEvent(
  @JvmField val state: String?,
  @JvmField val agentEventKind: String?,
  @JvmField val status: String?,
  @JvmField val stepId: String?,
  @JvmField val modelUsageCosts: List<BigDecimal?>,
  @JvmField val changedProjectFilePaths: Set<String>,
)

private data class ParsedAgentEvent(
  @JvmField val kind: String?,
  @JvmField val status: String?,
  @JvmField val stepId: String?,
  @JvmField val modelUsageCosts: List<BigDecimal?>,
  @JvmField val changedProjectFilePaths: Set<String>,
)

private data class ParsedAcpEvent(
  @JvmField val sessionUpdate: String?,
  @JvmField val status: String?,
)

private data class EventsFileStat(
  @JvmField val lastModifiedMillis: Long,
  @JvmField val size: Long,
)

private data class CachedEventsAnalysis(
  @JvmField val stat: EventsFileStat,
  @JvmField val analysis: JunieSessionEventsAnalysis,
)

private data class CostCacheKey(
  @JvmField val sessionId: String,
  @JvmField val updatedAt: Long,
)

private data class CachedCost(
  @JvmField val cost: AgentSessionCost?,
)

private val RUNNING_STATUS_TOKENS = setOf("inprogress", "running", "started", "pending")
private val TERMINAL_STATUS_TOKENS = setOf("completed", "complete", "finished", "failed", "failure", "canceled", "cancelled", "done")
private const val UNKNOWN_TERMINAL_STEP_ID = "__junie_unknown_terminal_step__"
