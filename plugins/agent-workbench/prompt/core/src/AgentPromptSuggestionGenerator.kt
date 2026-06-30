// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.core

// @spec plugins/ij-air/spec/actions/global-prompt-suggestions.spec.md

import com.intellij.platform.ai.agent.core.extensions.OverridableValue
import com.intellij.platform.ai.agent.core.extensions.SingleExtensionPointResolver
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

data class AgentPromptSuggestionRequest(
  @JvmField val project: Project,
  @JvmField val projectPath: String?,
  @JvmField val targetModeId: String,
  @JvmField val contextItems: List<AgentPromptContextItem>,
)

enum class AgentPromptSuggestionProvenance {
  TEMPLATE,
  AI_POLISHED,
  AI_GENERATED,
}

data class AgentPromptSuggestionCandidate(
  @JvmField val id: String,
  @JvmField val label: @NlsSafe String,
  @JvmField val promptText: @NlsSafe String,
  @JvmField val provenance: AgentPromptSuggestionProvenance = AgentPromptSuggestionProvenance.TEMPLATE,
)

data class AgentPromptSuggestionUpdate(
  @JvmField val candidates: List<AgentPromptSuggestionCandidate>,
)

data class AgentPromptSuggestionAiRequest(
  @JvmField val project: Project,
  @JvmField val projectPath: String?,
  @JvmField val targetModeId: String,
  @JvmField val contextItems: List<AgentPromptContextItem>,
  @JvmField val seedCandidates: List<AgentPromptSuggestionCandidate>,
  @JvmField val maxGeneratedCandidates: Int = DEFAULT_AI_GENERATED_SUGGESTION_CANDIDATES,
)

data class AgentPromptSuggestionAiCandidate(
  @JvmField val id: String? = null,
  @JvmField val label: @NlsSafe String,
  @JvmField val promptText: @NlsSafe String,
)

sealed interface AgentPromptSuggestionAiResult {
  data class PolishedSeeds(
    @JvmField val candidates: List<AgentPromptSuggestionAiCandidate>,
  ) : AgentPromptSuggestionAiResult

  data class GeneratedCandidates(
    @JvmField val candidates: List<AgentPromptSuggestionAiCandidate>,
  ) : AgentPromptSuggestionAiResult
}

fun interface AgentPromptSuggestionGenerator {
  fun generateSuggestions(request: AgentPromptSuggestionRequest): Flow<AgentPromptSuggestionUpdate>
}

fun interface AgentPromptSuggestionAiBackend {
  suspend fun generateSuggestionResult(request: AgentPromptSuggestionAiRequest): AgentPromptSuggestionAiResult?
}

private class AgentPromptSuggestionGeneratorRegistryLog

private val LOG = logger<AgentPromptSuggestionGeneratorRegistryLog>()
private const val DEFAULT_AI_GENERATED_SUGGESTION_CANDIDATES = 3
private const val MAX_AI_GENERATED_LABEL_LENGTH = 120
private const val MAX_AI_GENERATED_PROMPT_TEXT_LENGTH = 1_000
private val AGENT_PROMPT_SUGGESTION_AI_BACKEND_EP: ExtensionPointName<AgentPromptSuggestionAiBackend> =
  ExtensionPointName("com.intellij.agent.workbench.promptSuggestionAiBackend")

private val REGISTERED_PROMPT_SUGGESTION_AI_BACKEND = SingleExtensionPointResolver(
  log = LOG,
  extensionPoint = AGENT_PROMPT_SUGGESTION_AI_BACKEND_EP,
  unavailableMessage = "Prompt suggestion AI backend EP is unavailable in this context",
  multipleExtensionsMessage = { backends ->
    "Multiple prompt suggestion AI backends registered; using first: ${backends.map { it::class.java.name }}"
  },
)

private class DefaultAgentPromptSuggestionGenerator(
  private val aiBackendProvider: () -> AgentPromptSuggestionAiBackend? = AgentPromptSuggestionAiBackends::find,
) : AgentPromptSuggestionGenerator {
  override fun generateSuggestions(request: AgentPromptSuggestionRequest): Flow<AgentPromptSuggestionUpdate> = flow {
    val fallbackCandidates = AgentPromptSuggestionSeeds.buildDefaultSuggestions(request.contextItems)
    emit(AgentPromptSuggestionUpdate(fallbackCandidates))

    val aiBackend = aiBackendProvider() ?: return@flow
    val aiRequest = request.toAgentPromptSuggestionAiRequest(fallbackCandidates)
    val aiUpdate = try {
      aiBackend.generateSuggestionResult(aiRequest)?.toSuggestionUpdate(aiRequest)
    }
                   catch (t: Throwable) {
                     if (t is CancellationException) {
                       throw t
                     }
                     LOG.warn("Failed to load AI prompt suggestions", t)
                     null
                   } ?: return@flow

    emit(aiUpdate)
  }
}

object AgentPromptSuggestionAiBackends {
  private val backendOverride = OverridableValue { REGISTERED_PROMPT_SUGGESTION_AI_BACKEND.findFirstOrNull() }

  fun find(): AgentPromptSuggestionAiBackend? {
    return backendOverride.value()
  }

  @Suppress("unused")
  fun <T> withBackendForTest(backend: AgentPromptSuggestionAiBackend?, action: () -> T): T {
    return backendOverride.withOverride(backend, action)
  }
}

object AgentPromptSuggestionGenerators {
  private val generatorOverride = OverridableValue<AgentPromptSuggestionGenerator?> { DefaultAgentPromptSuggestionGenerator() }

  fun find(): AgentPromptSuggestionGenerator? {
    return generatorOverride.value()
  }
}

private fun AgentPromptSuggestionRequest.toAgentPromptSuggestionAiRequest(
  fallbackCandidates: List<AgentPromptSuggestionCandidate>,
): AgentPromptSuggestionAiRequest {
  return AgentPromptSuggestionAiRequest(
    project = project,
    projectPath = projectPath,
    targetModeId = targetModeId,
    contextItems = contextItems,
    seedCandidates = fallbackCandidates,
  )
}

private fun AgentPromptSuggestionAiResult.toSuggestionUpdate(request: AgentPromptSuggestionAiRequest): AgentPromptSuggestionUpdate? {
  return when (this) {
    is AgentPromptSuggestionAiResult.GeneratedCandidates -> validateGeneratedCandidates(
      request = request,
      generatedCandidates = candidates,
    )?.let(::AgentPromptSuggestionUpdate)
    is AgentPromptSuggestionAiResult.PolishedSeeds -> validatePolishedCandidates(
      fallbackCandidates = request.seedCandidates,
      polishedCandidates = candidates,
    )?.let(::AgentPromptSuggestionUpdate)
  }
}

private fun validatePolishedCandidates(
  fallbackCandidates: List<AgentPromptSuggestionCandidate>,
  polishedCandidates: List<AgentPromptSuggestionAiCandidate>,
): List<AgentPromptSuggestionCandidate>? {
  if (fallbackCandidates.isEmpty() || polishedCandidates.isEmpty()) {
    LOG.debug("Ignoring polished AI prompt suggestions because there are no fallback seeds to polish")
    return null
  }
  if (polishedCandidates.size != fallbackCandidates.size) {
    LOG.debug("Ignoring polished AI prompt suggestions because the candidate count does not match fallback seeds")
    return null
  }

  val validated = ArrayList<AgentPromptSuggestionCandidate>(fallbackCandidates.size)
  fallbackCandidates.forEachIndexed { index, fallbackCandidate ->
    val normalized = normalizeSuggestionCandidate(polishedCandidates[index])
    if (normalized?.id != fallbackCandidate.id) {
      LOG.debug("Ignoring polished AI prompt suggestions because the slot ids do not match fallback seeds")
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
  request: AgentPromptSuggestionAiRequest,
  generatedCandidates: List<AgentPromptSuggestionAiCandidate>,
): List<AgentPromptSuggestionCandidate>? {
  if (generatedCandidates.isEmpty()) {
    LOG.debug("Ignoring generated AI prompt suggestions because the backend returned no candidates")
    return null
  }

  val prefix = resolveGeneratedSuggestionPrefix(request.seedCandidates, request.contextItems)
  val seenSuggestions = LinkedHashSet<String>()
  val maxCandidates = request.maxGeneratedCandidates.coerceAtLeast(1)
  val validated = ArrayList<AgentPromptSuggestionCandidate>(generatedCandidates.size.coerceAtMost(maxCandidates))
  generatedCandidates.forEachIndexed { index, generatedCandidate ->
    if (validated.size >= maxCandidates) {
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
    LOG.debug("Ignoring AI prompt suggestions because all generated candidates were invalid or duplicates")
    return null
  }
  return validated
}

private fun normalizeSuggestionCandidate(candidate: AgentPromptSuggestionAiCandidate): AgentPromptSuggestionAiCandidate? {
  val label = normalizeGeneratedLabel(candidate.label) ?: return null
  val promptText = normalizeGeneratedPromptText(candidate.promptText) ?: return null
  return AgentPromptSuggestionAiCandidate(
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

private fun buildGeneratedSuggestionDedupeKey(candidate: AgentPromptSuggestionAiCandidate): String {
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
    contextItems.any {
      it.rendererId in setOf(AgentPromptContextRendererIds.SNIPPET,
                             AgentPromptContextRendererIds.FILE,
                             AgentPromptContextRendererIds.SYMBOL)
    } -> "editor"
    contextItems.any { it.rendererId == AgentPromptContextRendererIds.PATHS } -> "paths"
    else -> "context"
  }
}
