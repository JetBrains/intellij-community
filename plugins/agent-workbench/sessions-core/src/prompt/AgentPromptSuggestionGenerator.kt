// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.core.prompt

// @spec community/plugins/agent-workbench/spec/actions/global-prompt-suggestions.spec.md

import com.intellij.agent.workbench.sessions.core.OverridableValue
import com.intellij.agent.workbench.sessions.core.SingleExtensionPointResolver
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

fun interface AgentPromptSuggestionGenerator {
  fun generateSuggestions(request: AgentPromptSuggestionRequest): Flow<AgentPromptSuggestionUpdate>
}

fun interface AgentPromptSuggestionAiBackend {
  suspend fun generateSuggestionUpdate(
    request: AgentPromptSuggestionRequest,
    fallbackCandidates: List<AgentPromptSuggestionCandidate>,
  ): AgentPromptSuggestionUpdate?
}

private class AgentPromptSuggestionGeneratorRegistryLog

private val LOG = logger<AgentPromptSuggestionGeneratorRegistryLog>()
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
    val aiUpdate = try {
      aiBackend.generateSuggestionUpdate(request, fallbackCandidates)
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

  @Suppress("unused")
  fun <T> withGeneratorForTest(generator: AgentPromptSuggestionGenerator?, action: () -> T): T {
    return generatorOverride.withOverride(generator, action)
  }
}
