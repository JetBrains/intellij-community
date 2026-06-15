// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.core

import com.intellij.agent.workbench.common.session.AgentSessionLaunchMode
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.common.session.AgentSessionThread
import com.intellij.openapi.util.NlsSafe
import kotlinx.serialization.Serializable

data class AgentPromptContextItem(
  @JvmField val rendererId: String,
  @JvmField val title: String?,
  @JvmField val body: String,
  @JvmField val payload: AgentPromptPayloadValue = AgentPromptPayloadValue.Obj.EMPTY,
  @JvmField val itemId: String? = null,
  @JvmField val parentItemId: String? = null,
  @JvmField val source: String = "unknown",
  @JvmField val phase: AgentPromptContextContributorPhase? = null,
  @JvmField val truncation: AgentPromptContextTruncation = AgentPromptContextTruncation.none(body.length),
)

sealed interface AgentPromptPayloadValue {
  data class Obj(
    @JvmField val fields: Map<String, AgentPromptPayloadValue>,
  ) : AgentPromptPayloadValue {
    companion object {
      @JvmField
      val EMPTY: Obj = Obj(emptyMap())
    }
  }

  data class Arr(
    @JvmField val items: List<AgentPromptPayloadValue>,
  ) : AgentPromptPayloadValue

  data class Str(
    @JvmField val value: String,
  ) : AgentPromptPayloadValue

  data class Num(
    @JvmField val value: String,
  ) : AgentPromptPayloadValue

  data class Bool(
    @JvmField val value: Boolean,
  ) : AgentPromptPayloadValue

  data object Null : AgentPromptPayloadValue
}

enum class AgentPromptContextTruncationReason {
  NONE,
  SOURCE_LIMIT,
  SOFT_CAP_PARTIAL,
  SOFT_CAP_OMITTED,
}

data class AgentPromptContextTruncation(
  @JvmField val originalChars: Int,
  @JvmField val includedChars: Int,
  @JvmField val reason: AgentPromptContextTruncationReason,
) {
  companion object {
    fun none(chars: Int): AgentPromptContextTruncation {
      return AgentPromptContextTruncation(
        originalChars = chars.coerceAtLeast(0),
        includedChars = chars.coerceAtLeast(0),
        reason = AgentPromptContextTruncationReason.NONE,
      )
    }
  }
}

data class AgentPromptInitialMessageRequest(
  @JvmField val prompt: String,
  @JvmField val projectPath: String? = null,
  @JvmField val contextItems: List<AgentPromptContextItem> = emptyList(),
  @JvmField val contextEnvelopeSummary: AgentPromptContextEnvelopeSummary? = null,
  @JvmField val providerOptionIds: Set<String> = emptySet(),
)

@Serializable
enum class AgentPromptReasoningEffort {
  AUTO,
  LOW,
  MEDIUM,
  HIGH,
  XHIGH,
  MAX,
}

@Serializable
data class AgentPromptGenerationSettings(
  @JvmField val modelId: String? = null,
  @JvmField val reasoningEffort: AgentPromptReasoningEffort = AgentPromptReasoningEffort.AUTO,
  @JvmField val planReasoningEffort: AgentPromptReasoningEffort? = null,
) {
  companion object {
    @JvmField
    val AUTO: AgentPromptGenerationSettings = AgentPromptGenerationSettings()
  }
}

@Serializable
enum class AgentPromptLaunchProfileKind {
  BUILT_IN,
  USER,
}

@Serializable
data class AgentPromptLaunchProfile(
  @JvmField val id: String,
  @JvmField val name: @NlsSafe String,
  @JvmField val kind: AgentPromptLaunchProfileKind = AgentPromptLaunchProfileKind.USER,
  @JvmField val providerId: String,
  @JvmField val launchMode: AgentSessionLaunchMode = AgentSessionLaunchMode.STANDARD,
  @JvmField val generationSettings: AgentPromptGenerationSettings = AgentPromptGenerationSettings.AUTO,
)

data class AgentPromptGenerationModel(
  @JvmField val id: String,
  @JvmField val displayName: @NlsSafe String,
  @JvmField val supportedReasoningEfforts: Set<AgentPromptReasoningEffort> = emptySet(),
  @JvmField val defaultReasoningEffort: AgentPromptReasoningEffort? = null,
  @JvmField val isDefault: Boolean = false,
) {
  @JvmField
  var group: AgentPromptGenerationModelGroup = AgentPromptGenerationModelGroup.OTHER
}

fun AgentPromptGenerationModel.withGroup(group: AgentPromptGenerationModelGroup): AgentPromptGenerationModel {
  return copy().also { model -> model.group = group }
}

enum class AgentPromptGenerationModelGroup {
  LOCAL,
  OPENAI,
  CLAUDE_CODE,
  OTHER,
}

data class AgentPromptContextEnvelopeSummary(
  @JvmField val softCapChars: Int = 12_000,
  @JvmField val softCapExceeded: Boolean = false,
  @JvmField val autoTrimApplied: Boolean = false,
)

data class AgentPromptLaunchRequest(
  val provider: AgentSessionProvider,
  @JvmField val projectPath: String,
  @JvmField val launchMode: AgentSessionLaunchMode,
  @JvmField val initialMessageRequest: AgentPromptInitialMessageRequest,
  @JvmField val targetThreadId: String? = null,
  @JvmField val preferredDedicatedFrame: Boolean? = null,
  @JvmField val generationSettings: AgentPromptGenerationSettings = AgentPromptGenerationSettings.AUTO,
  @JvmField val generationModelCatalog: List<AgentPromptGenerationModel> = emptyList(),
  @JvmField val containerMode: Boolean = false,
  /**
   * Extra environment variables to inject into the terminal launch spec.
   * Used by the EEL container flow to pass `AGENT_CONTAINER_SESSION_ID` and
   * `AGENT_CONTAINER_WORKSPACE_PATH` to the Claude Code process on the host.
   * Merged into the launch spec after standard augmentation.
   */
  @JvmField val containerSessionEnvVariables: Map<String, String> = emptyMap(),
  /**
   * Extra command-line arguments appended to the agent CLI command.
   * Used by the EEL container flow to add `--disallowedTools` so that
   * Claude Code's built-in file/bash tools are disabled, forcing all
   * operations through the ij-proxy MCP tools (which route to the container).
   */
  @JvmField val containerSessionExtraArgs: List<String> = emptyList(),
)

data class AgentPromptProjectPathCandidate(
  @JvmField val path: @NlsSafe String,
  @JvmField val displayName: @NlsSafe String,
)

data class AgentPromptAddContextTargetCandidate(
  @JvmField val projectPath: @NlsSafe String,
  val provider: AgentSessionProvider,
  @JvmField val launchMode: AgentSessionLaunchMode = AgentSessionLaunchMode.STANDARD,
  @JvmField val threadId: @NlsSafe String,
  @JvmField val displayText: @NlsSafe String,
  @JvmField val secondaryText: @NlsSafe String = "",
  @JvmField val selected: Boolean = false,
)

data class AgentPromptAddContextToTargetRequest(
  @JvmField val target: AgentPromptAddContextTargetCandidate,
  @JvmField val contextItems: List<AgentPromptContextItem>,
)

enum class AgentPromptAddContextToTargetResult {
  ADDED_TO_CHAT,
  ALREADY_ADDED_TO_CHAT,
  UNAVAILABLE,
}

enum class AgentPromptLaunchError {
  PROVIDER_UNAVAILABLE,
  UNSUPPORTED_LAUNCH_MODE,
  TARGET_THREAD_NOT_FOUND,
  TARGET_THREAD_BUSY_FOR_PLAN_MODE,
  CANCELLED,
  DROPPED_DUPLICATE,
  INTERNAL_ERROR,
}

data class AgentPromptLaunchResult(
  @JvmField val launched: Boolean,
  @JvmField val error: AgentPromptLaunchError? = null,
) {
  companion object {
    @JvmField
    val SUCCESS: AgentPromptLaunchResult = AgentPromptLaunchResult(launched = true)

    fun failure(error: AgentPromptLaunchError): AgentPromptLaunchResult {
      return AgentPromptLaunchResult(launched = false, error = error)
    }
  }
}

data class AgentPromptExistingThreadsSnapshot(
  @JvmField val threads: List<AgentSessionThread>,
  @JvmField val isLoading: Boolean,
  @JvmField val hasLoaded: Boolean,
  @JvmField val hasError: Boolean,
)
