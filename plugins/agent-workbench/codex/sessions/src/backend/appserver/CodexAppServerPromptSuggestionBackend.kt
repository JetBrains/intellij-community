// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.codex.sessions.backend.appserver

// @spec community/plugins/agent-workbench/spec/actions/global-prompt-suggestions.spec.md

import com.intellij.agent.workbench.codex.common.CodexAppServerException
import com.intellij.agent.workbench.codex.common.CodexAppServerValue
import com.intellij.agent.workbench.codex.common.CodexCliNotFoundException
import com.intellij.agent.workbench.codex.common.CodexPromptSuggestionCandidate
import com.intellij.agent.workbench.codex.common.CodexPromptSuggestionContextItem
import com.intellij.agent.workbench.codex.common.CodexPromptSuggestionContextTruncation
import com.intellij.agent.workbench.codex.common.CodexPromptSuggestionRequest
import com.intellij.agent.workbench.codex.common.CodexPromptSuggestionResult
import com.intellij.agent.workbench.codex.common.normalizeRootPath
import com.intellij.agent.workbench.prompt.core.AgentPromptContextItem
import com.intellij.agent.workbench.prompt.core.AgentPromptContextRendererIds
import com.intellij.agent.workbench.prompt.core.AgentPromptContextTruncation
import com.intellij.agent.workbench.prompt.core.AgentPromptPayloadValue
import com.intellij.agent.workbench.prompt.core.AgentPromptSuggestionAiBackend
import com.intellij.agent.workbench.prompt.core.AgentPromptSuggestionCandidate
import com.intellij.agent.workbench.prompt.core.AgentPromptSuggestionProvenance
import com.intellij.agent.workbench.prompt.core.AgentPromptSuggestionRequest
import com.intellij.agent.workbench.prompt.core.AgentPromptSuggestionUpdate
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.NlsSafe
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.milliseconds

private class CodexAppServerPromptSuggestionBackendLog

private val LOG = logger<CodexAppServerPromptSuggestionBackendLog>()

private const val PROMPT_SUGGESTION_TIMEOUT_MS = 2_000L
private const val MAX_AI_GENERATED_SUGGESTION_CANDIDATES = 3
private const val MAX_AI_GENERATED_LABEL_LENGTH = 120
private const val MAX_AI_GENERATED_PROMPT_TEXT_LENGTH = 1_000

internal class CodexAppServerPromptSuggestionBackend(
  private val suggestWithCodexAppServer: suspend (CodexPromptSuggestionRequest) -> CodexPromptSuggestionResult? = { request ->
    serviceAsync<CodexPromptSuggestionAppServerService>().suggestPrompt(request)
  },
  private val timeoutMs: Long = PROMPT_SUGGESTION_TIMEOUT_MS,
  private val isSuggestionGenerationDisabled: () -> Boolean = CodexPromptSuggestionModelSettings::isDisabled,
  private val modelProvider: () -> String = CodexPromptSuggestionModelSettings::getModel,
  private val reasoningEffortProvider: () -> String = CodexPromptSuggestionModelSettings::getReasoningEffort,
) : AgentPromptSuggestionAiBackend {
  override suspend fun generateSuggestionUpdate(
    request: AgentPromptSuggestionRequest,
    fallbackCandidates: List<AgentPromptSuggestionCandidate>,
  ): AgentPromptSuggestionUpdate? {
    if (isSuggestionGenerationDisabled()) {
      return null
    }

    val codexRequest = request.toCodexPromptSuggestionRequest(
      model = modelProvider(),
      reasoningEffort = reasoningEffortProvider(),
      fallbackCandidates = fallbackCandidates,
    )
    var generationFailed = false
    val suggestionResult = try {
      withTimeoutOrNull(timeoutMs.milliseconds) {
        suggestWithCodexAppServer(codexRequest)
      }
    }
    catch (_: CodexCliNotFoundException) {
      generationFailed = true
      LOG.debug { "Skipping Codex prompt suggestion generation because the Codex CLI is unavailable" }
      null
    }
    catch (t: CodexAppServerException) {
      generationFailed = true
      LOG.warn("Failed to load Codex prompt suggestions", t)
      null
    }
    catch (t: Throwable) {
      if (t is CancellationException) {
        throw t
      }
      generationFailed = true
      LOG.warn("Failed to load Codex prompt suggestions", t)
      null
    }

    if (suggestionResult == null) {
      if (!generationFailed) {
        LOG.debug { "Codex prompt suggestion refinement timed out or returned no result" }
      }
      return null
    }

    return validateSuggestionResult(
      fallbackCandidates = fallbackCandidates,
      contextItems = request.contextItems,
      suggestionResult = suggestionResult,
    )
  }

  private fun validateSuggestionResult(
    fallbackCandidates: List<AgentPromptSuggestionCandidate>,
    contextItems: List<AgentPromptContextItem>,
    suggestionResult: CodexPromptSuggestionResult,
  ): AgentPromptSuggestionUpdate? {
    return when (suggestionResult) {
      is CodexPromptSuggestionResult.GeneratedCandidates -> validateGeneratedCandidates(
        fallbackCandidates = fallbackCandidates,
        contextItems = contextItems,
        generatedCandidates = suggestionResult.candidates,
      )?.let(::AgentPromptSuggestionUpdate)
      is CodexPromptSuggestionResult.PolishedSeeds -> validatePolishedCandidates(
        fallbackCandidates = fallbackCandidates,
        polishedCandidates = suggestionResult.candidates,
      )?.let(::AgentPromptSuggestionUpdate)
    }
  }

  private fun validatePolishedCandidates(
    fallbackCandidates: List<AgentPromptSuggestionCandidate>,
    polishedCandidates: List<CodexPromptSuggestionCandidate>,
  ): List<AgentPromptSuggestionCandidate>? {
    if (fallbackCandidates.isEmpty() || polishedCandidates.isEmpty()) {
      LOG.debug { "Ignoring Codex polished prompt suggestions because there are no fallback seeds to polish" }
      return null
    }
    if (polishedCandidates.size != fallbackCandidates.size) {
      LOG.debug { "Ignoring Codex polished prompt suggestions because the candidate count does not match fallback seeds" }
      return null
    }

    val validated = ArrayList<AgentPromptSuggestionCandidate>(fallbackCandidates.size)
    fallbackCandidates.forEachIndexed { index, fallbackCandidate ->
      val normalized = normalizeSuggestionCandidate(polishedCandidates[index])
      if (normalized?.id != fallbackCandidate.id) {
        LOG.debug { "Ignoring Codex polished prompt suggestions because the slot ids do not match fallback seeds" }
        return null
      }
      validated += AgentPromptSuggestionCandidate(
        id = fallbackCandidate.id,
        label = normalized.label,
        promptText = normalized.promptText,
        provenance = AgentPromptSuggestionProvenance.AI_POLISHED,
      )
    }
    return validated
  }

  private fun validateGeneratedCandidates(
    fallbackCandidates: List<AgentPromptSuggestionCandidate>,
    contextItems: List<AgentPromptContextItem>,
    generatedCandidates: List<CodexPromptSuggestionCandidate>,
  ): List<AgentPromptSuggestionCandidate>? {
    if (generatedCandidates.isEmpty()) {
      LOG.debug { "Ignoring Codex generated prompt suggestions because the app-server returned no candidates" }
      return null
    }

    val prefix = resolveGeneratedSuggestionPrefix(fallbackCandidates, contextItems)
    val seenSuggestions = LinkedHashSet<String>()
    val validated = ArrayList<AgentPromptSuggestionCandidate>(generatedCandidates.size.coerceAtMost(MAX_AI_GENERATED_SUGGESTION_CANDIDATES))
    generatedCandidates.forEachIndexed { index, generatedCandidate ->
      if (validated.size >= MAX_AI_GENERATED_SUGGESTION_CANDIDATES) {
        return@forEachIndexed
      }
      val normalized = normalizeSuggestionCandidate(generatedCandidate) ?: return@forEachIndexed
      val dedupeKey = buildGeneratedSuggestionDedupeKey(normalized)
      if (!seenSuggestions.add(dedupeKey)) {
        return@forEachIndexed
      }
      validated += AgentPromptSuggestionCandidate(
        id = buildGeneratedSuggestionId(prefix = prefix, label = normalized.label, index = index),
        label = normalized.label,
        promptText = normalized.promptText,
        provenance = AgentPromptSuggestionProvenance.AI_GENERATED,
      )
    }
    if (validated.isEmpty()) {
      LOG.debug { "Ignoring Codex prompt suggestions because all app-server candidates were invalid or duplicates" }
      return null
    }
    return validated
  }
}

private fun AgentPromptSuggestionRequest.toCodexPromptSuggestionRequest(
  model: String,
  reasoningEffort: String,
  fallbackCandidates: List<AgentPromptSuggestionCandidate>,
): CodexPromptSuggestionRequest {
  return CodexPromptSuggestionRequest(
    cwd = projectPath
      ?.trim()
      ?.takeIf { it.isNotEmpty() }
      ?.let(::normalizeRootPath),
    targetMode = when (targetModeId) {
      "NEW_TASK" -> "new_task"
      "EXISTING_TASK" -> "existing_task"
      else -> targetModeId.lowercase()
    },
    model = model,
    reasoningEffort = reasoningEffort,
    maxCandidates = MAX_AI_GENERATED_SUGGESTION_CANDIDATES,
    contextItems = contextItems.map(AgentPromptContextItem::toCodexPromptSuggestionContextItem),
    seedCandidates = fallbackCandidates.map(AgentPromptSuggestionCandidate::toCodexPromptSuggestionCandidate),
  )
}

private fun AgentPromptSuggestionCandidate.toCodexPromptSuggestionCandidate(): CodexPromptSuggestionCandidate {
  return CodexPromptSuggestionCandidate(
    id = id,
    label = label,
    promptText = promptText,
  )
}

private fun AgentPromptContextItem.toCodexPromptSuggestionContextItem(): CodexPromptSuggestionContextItem {
  return CodexPromptSuggestionContextItem(
    rendererId = rendererId,
    title = title,
    body = body,
    payload = payload.toCodexAppServerValue(),
    itemId = itemId,
    parentItemId = parentItemId,
    source = source,
    truncation = truncation.toCodexPromptSuggestionContextTruncation(),
  )
}

private fun AgentPromptContextTruncation.toCodexPromptSuggestionContextTruncation(): CodexPromptSuggestionContextTruncation {
  return CodexPromptSuggestionContextTruncation(
    originalChars = originalChars,
    includedChars = includedChars,
    reason = reason.name.lowercase(),
  )
}

private fun normalizeSuggestionCandidate(candidate: CodexPromptSuggestionCandidate): CodexPromptSuggestionCandidate? {
  val label = normalizeGeneratedLabel(candidate.label) ?: return null
  val promptText = normalizeGeneratedPromptText(candidate.promptText) ?: return null
  return CodexPromptSuggestionCandidate(
    id = candidate.id?.trim()?.takeIf { it.isNotEmpty() },
    label = label,
    promptText = promptText,
  )
}

private fun normalizeGeneratedLabel(label: String): @NlsSafe String? {
  return label
    .lineSequence()
    .map(String::trim)
    .filter(String::isNotEmpty)
    .joinToString(separator = " ")
    .take(MAX_AI_GENERATED_LABEL_LENGTH)
    .trim()
    .takeIf { it.isNotEmpty() }
}

private fun normalizeGeneratedPromptText(promptText: String): @NlsSafe String? {
  return promptText
    .trim()
    .take(MAX_AI_GENERATED_PROMPT_TEXT_LENGTH)
    .trim()
    .takeIf { it.isNotEmpty() }
}

private fun buildGeneratedSuggestionDedupeKey(candidate: CodexPromptSuggestionCandidate): String {
  return candidate.label.lowercase() + '\u0000' + candidate.promptText.lowercase()
}

private fun buildGeneratedSuggestionId(prefix: String, label: String, index: Int): String {
  val slug = buildString {
    label.lowercase().forEach { ch ->
      when {
        ch.isLetterOrDigit() -> append(ch)
        ch == ' ' || ch == '-' || ch == '_' || ch == '/' || ch == '.' -> {
          if (isNotEmpty() && last() != '-') {
            append('-')
          }
        }
      }
    }
  }.trim('-').ifEmpty { "candidate" }
  return "$prefix.generated.$slug.${index + 1}"
}

private fun resolveGeneratedSuggestionPrefix(
  fallbackCandidates: List<AgentPromptSuggestionCandidate>,
  contextItems: List<AgentPromptContextItem>,
): String {
  fallbackCandidates.firstOrNull()?.id
    ?.substringBefore('.')
    ?.takeIf { it.isNotBlank() }
    ?.let { return it }

  return when {
    contextItems.any { it.rendererId == AgentPromptContextRendererIds.TEST_FAILURES } -> "tests"
    contextItems.any { it.rendererId == AgentPromptContextRendererIds.VCS_COMMITS } -> "vcs"
    contextItems.any { it.rendererId in setOf(AgentPromptContextRendererIds.SNIPPET, AgentPromptContextRendererIds.FILE, AgentPromptContextRendererIds.SYMBOL) } -> "editor"
    contextItems.any { it.rendererId == AgentPromptContextRendererIds.PATHS } -> "paths"
    else -> "context"
  }
}

private fun AgentPromptPayloadValue.toCodexAppServerValue(): CodexAppServerValue {
  return when (this) {
    is AgentPromptPayloadValue.Arr -> CodexAppServerValue.Arr(items.map(AgentPromptPayloadValue::toCodexAppServerValue))
    is AgentPromptPayloadValue.Bool -> CodexAppServerValue.Bool(value)
    is AgentPromptPayloadValue.Null -> CodexAppServerValue.Null
    is AgentPromptPayloadValue.Num -> CodexAppServerValue.Num(value)
    is AgentPromptPayloadValue.Obj -> CodexAppServerValue.Obj(fields.mapValues { (_, item) -> item.toCodexAppServerValue() })
    is AgentPromptPayloadValue.Str -> CodexAppServerValue.Str(value)
  }
}
