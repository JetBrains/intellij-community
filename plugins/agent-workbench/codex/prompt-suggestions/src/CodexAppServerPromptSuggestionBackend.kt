// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.codex.prompt.suggestions

// @spec community/plugins/agent-workbench/spec/actions/global-prompt-suggestions.spec.md

import com.intellij.agent.workbench.codex.common.CodexAppServerException
import com.intellij.agent.workbench.codex.common.CodexCliNotFoundException
import com.intellij.agent.workbench.codex.common.invokeOnCompletionOrWarn
import com.intellij.agent.workbench.codex.common.normalizeRootPath
import com.intellij.agent.workbench.core.extensions.OverridableValue
import com.intellij.agent.workbench.prompt.core.AgentPromptContextItem
import com.intellij.agent.workbench.prompt.core.AgentPromptContextTruncation
import com.intellij.agent.workbench.prompt.core.AgentPromptSuggestionAiBackend
import com.intellij.agent.workbench.prompt.core.AgentPromptSuggestionAiCandidate
import com.intellij.agent.workbench.prompt.core.AgentPromptSuggestionAiRequest
import com.intellij.agent.workbench.prompt.core.AgentPromptSuggestionAiResult
import com.intellij.agent.workbench.prompt.core.AgentPromptSuggestionCandidate
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.options.advanced.AdvancedSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.milliseconds

private class CodexAppServerPromptSuggestionBackendLog

private val LOG = logger<CodexAppServerPromptSuggestionBackendLog>()

private const val PROMPT_SUGGESTION_TIMEOUT_MS = 2_000L

internal const val CODEX_PROMPT_SUGGESTION_MODEL_SETTING_ID: String = "agent.workbench.codex.prompt.suggestion.model"
internal const val DEFAULT_CODEX_PROMPT_SUGGESTION_MODEL: String = "gpt-5.4"
internal const val DEFAULT_CODEX_PROMPT_SUGGESTION_REASONING_EFFORT: String = "low"

private const val DISABLED_CODEX_PROMPT_SUGGESTION_MODEL: String = "off"

internal class CodexAppServerPromptSuggestionBackend(
  private val suggestWithCodexAppServer: suspend (CodexPromptSuggestionRequest) -> AgentPromptSuggestionAiResult? = { request ->
    serviceAsync<CodexPromptSuggestionAppServerService>().suggestPrompt(request)
  },
  private val timeoutMs: Long = PROMPT_SUGGESTION_TIMEOUT_MS,
  private val isSuggestionGenerationDisabled: () -> Boolean = CodexPromptSuggestionModelSettings::isDisabled,
  private val modelProvider: () -> String = CodexPromptSuggestionModelSettings::getModel,
  private val reasoningEffortProvider: () -> String = CodexPromptSuggestionModelSettings::getReasoningEffort,
) : AgentPromptSuggestionAiBackend {
  override suspend fun generateSuggestionResult(request: AgentPromptSuggestionAiRequest): AgentPromptSuggestionAiResult? {
    if (isSuggestionGenerationDisabled()) {
      return null
    }

    val codexRequest = request.toCodexPromptSuggestionRequest(
      model = modelProvider(),
      reasoningEffort = reasoningEffortProvider(),
    )
    var generationFailed = false
    val suggestionResult = try {
      withTimeoutOrNull(timeoutMs.milliseconds) {
        suggestWithCodexAppServer(codexRequest)
      }
    }
    catch (_: CodexCliNotFoundException) {
      generationFailed = true
      LOG.debug("Skipping Codex prompt suggestion generation because the Codex CLI is unavailable")
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

    if (suggestionResult == null && !generationFailed) {
      LOG.debug("Codex prompt suggestion refinement timed out or returned no result")
    }
    return suggestionResult
  }
}

@Service(Service.Level.APP)
class CodexPromptSuggestionAppServerService internal constructor(
  serviceScope: CoroutineScope,
  private val suggestWithClient: suspend (CodexPromptSuggestionRequest) -> AgentPromptSuggestionAiResult?,
  private val shutdownClient: () -> Unit = {},
) {
  @Suppress("unused")
  constructor(serviceScope: CoroutineScope) : this(
    serviceScope = serviceScope,
    client = CodexPromptSuggestionAppServerClient(
      coroutineScope = serviceScope,
    ),
  )

  @Suppress("unused")
  private constructor(serviceScope: CoroutineScope, client: CodexPromptSuggestionAppServerClient) : this(
    serviceScope = serviceScope,
    suggestWithClient = client::suggestPrompt,
    shutdownClient = client::shutdown,
  )

  private val suggestionMutex = Mutex()

  init {
    serviceScope.invokeOnCompletionOrWarn(
      log = LOG,
      missingJobMessage = "Codex prompt suggestion service scope has no Job; shutdown hook not installed",
      action = shutdownClient,
    )
  }

  internal suspend fun suggestPrompt(request: CodexPromptSuggestionRequest): AgentPromptSuggestionAiResult? {
    return suggestionMutex.withLock {
      currentCoroutineContext().ensureActive()
      suggestWithClient(request)
    }
  }
}

internal object CodexPromptSuggestionModelSettings {
  private val configuredValueProvider = OverridableValue {
    AdvancedSettings.getString(CODEX_PROMPT_SUGGESTION_MODEL_SETTING_ID).trim()
  }

  fun getModel(): String {
    val configuredValue = readConfiguredValue()
    return if (configuredValue.isBlank() || configuredValue.equals(DISABLED_CODEX_PROMPT_SUGGESTION_MODEL, ignoreCase = true)) {
      DEFAULT_CODEX_PROMPT_SUGGESTION_MODEL
    }
    else {
      configuredValue
    }
  }

  fun isDisabled(): Boolean {
    return readConfiguredValue().equals(DISABLED_CODEX_PROMPT_SUGGESTION_MODEL, ignoreCase = true)
  }

  fun getReasoningEffort(): String {
    return DEFAULT_CODEX_PROMPT_SUGGESTION_REASONING_EFFORT
  }

  fun <T> withConfiguredValueForTest(value: String, action: () -> T): T {
    return configuredValueProvider.withOverride(value.trim(), action)
  }

  private fun readConfiguredValue(): String {
    return configuredValueProvider.value()
  }
}

private fun AgentPromptSuggestionAiRequest.toCodexPromptSuggestionRequest(
  model: String,
  reasoningEffort: String,
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
    maxCandidates = maxGeneratedCandidates,
    contextItems = contextItems.map(AgentPromptContextItem::toCodexPromptSuggestionContextItem),
    seedCandidates = seedCandidates.map(AgentPromptSuggestionCandidate::toAgentPromptSuggestionAiCandidate),
  )
}

private fun AgentPromptSuggestionCandidate.toAgentPromptSuggestionAiCandidate(): AgentPromptSuggestionAiCandidate {
  return AgentPromptSuggestionAiCandidate(
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
    payload = payload,
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
