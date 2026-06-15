// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.core.providers

// @spec community/plugins/agent-workbench/spec/sessions/agent-terminal-sessions.spec.md

import com.intellij.agent.workbench.common.session.AgentSessionLaunchMode
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.common.parseAgentThreadIdentity
import com.intellij.agent.workbench.sessions.core.isAgentSessionPendingThreadId
import com.intellij.agent.workbench.prompt.core.AgentPromptContextEnvelopeFormatter
import com.intellij.agent.workbench.prompt.core.AgentPromptGenerationSettings
import com.intellij.agent.workbench.prompt.core.AgentPromptGenerationModel
import com.intellij.agent.workbench.prompt.core.AgentPromptInitialMessageRequest
import com.intellij.agent.workbench.prompt.core.AgentPromptReasoningEffort
import com.intellij.agent.workbench.prompt.core.AgentPromptReusableSourceEntry
import com.intellij.agent.workbench.sessions.core.settings.AgentWorkbenchCheckboxSetting
import com.intellij.openapi.project.Project
import javax.swing.Icon
import javax.swing.JComponent

enum class AgentInitialMessageStartupPolicy {
  TRY_STARTUP_COMMAND,
  POST_START_ONLY,
}

enum class AgentInitialMessageMode {
  STANDARD,
  PLAN,
}

enum class AgentInitialMessageTimeoutPolicy {
  ALLOW_TIMEOUT_FALLBACK,
  REQUIRE_EXPLICIT_READINESS,
}

enum class AgentInitialMessageDispatchCompletionPolicy {
  IMMEDIATE,
  RETRY_ON_CODEX_PLAN_BUSY,
}

enum class AgentInitialMessageDispatchAction {
  SEND_TEXT,
  ENSURE_TERMINAL_PLAN_MODE,
}

/**
 * Controls how synchronous provider pickers behave before CLI availability is known.
 */
enum class AgentSessionProviderCliVisibilityPolicy {
  PROMINENT,
  DISCOVER_WHEN_AVAILABLE,
}

typealias AgentThreadRenameAction = suspend (path: String, threadId: String, normalizedName: String) -> Boolean

data class AgentInitialMessagePlan(
  @JvmField val message: String?,
  @JvmField val mode: AgentInitialMessageMode = AgentInitialMessageMode.STANDARD,
  @JvmField val startupPolicy: AgentInitialMessageStartupPolicy = AgentInitialMessageStartupPolicy.TRY_STARTUP_COMMAND,
  @JvmField val timeoutPolicy: AgentInitialMessageTimeoutPolicy = AgentInitialMessageTimeoutPolicy.ALLOW_TIMEOUT_FALLBACK,
) {
  companion object {
    @JvmField
    val EMPTY: AgentInitialMessagePlan = AgentInitialMessagePlan(message = null)

    fun composeDefault(request: AgentPromptInitialMessageRequest): AgentInitialMessagePlan {
      val message = AgentPromptContextEnvelopeFormatter.composeInitialMessage(request)
        .trim()
        .takeIf { it.isNotEmpty() }
      return AgentInitialMessagePlan(message = message)
    }
  }
}

data class AgentInitialMessageDispatchPlan(
  @JvmField val startupLaunchSpecOverride: AgentSessionTerminalLaunchSpec? = null,
  @JvmField val postStartDispatchSteps: List<AgentInitialMessageDispatchStep> = emptyList(),
  @JvmField val initialMessageToken: String? = null,
) {
  companion object {
    @JvmField
    val EMPTY: AgentInitialMessageDispatchPlan = AgentInitialMessageDispatchPlan()
  }
}

data class AgentInitialMessageDispatchStep(
  @JvmField val text: String = "",
  @JvmField val timeoutPolicy: AgentInitialMessageTimeoutPolicy = AgentInitialMessageTimeoutPolicy.ALLOW_TIMEOUT_FALLBACK,
  @JvmField val completionPolicy: AgentInitialMessageDispatchCompletionPolicy = AgentInitialMessageDispatchCompletionPolicy.IMMEDIATE,
  @JvmField val action: AgentInitialMessageDispatchAction = AgentInitialMessageDispatchAction.SEND_TEXT,
) {
  fun isDispatchable(): Boolean {
    return action != AgentInitialMessageDispatchAction.SEND_TEXT || text.isNotBlank()
  }
}

data class AgentPendingSessionMetadata(
  @JvmField val createdAtMs: Long,
  @JvmField val launchMode: String?,
)

interface AgentSessionProviderDescriptor {
  val provider: AgentSessionProvider
  val displayNameKey: String
  val displayNameFallback: String
    get() = provider.value.replaceFirstChar { char ->
      if (char.isLowerCase()) char.titlecase() else char.toString()
    }
  val cliDisplayNameKey: String
    get() = displayNameKey
  val cliDisplayNameFallback: String
    get() = displayNameFallback
  val displayPriority: Int
    get() = Int.MAX_VALUE
  val newSessionLabelKey: String
  val newSessionTitleKey: String
    get() = "toolwindow.action.new.thread"
  val quickStartLabelKey: String
    get() = newSessionLabelKey
  val quickStartActionTextKey: String
    get() = "action.AgentWorkbenchSessions.NewThreadQuick.text"
  val quickStartActionDescriptionKey: String
    get() = "action.AgentWorkbenchSessions.NewThreadQuick.description"
  val quickStartActionTargetDescriptionKey: String?
    get() = null
  val newSessionDescriptionKey: String?
    get() = null
  val yoloSessionLabelKey: String?
    get() = null
  val yoloSessionModeLabelKey: String?
    get() = null
  val icon: Icon

  /** Desaturated variant for persistent surfaces (tree, toolbar button, tabs, status bar); menus keep [icon]. */
  val monochromeIcon: Icon
    get() = icon
  val promptOptions: List<AgentPromptProviderOption>
    get() = emptyList()
  val providerSettings: List<AgentWorkbenchCheckboxSetting>
    get() = emptyList()

  /**
   * Concrete reasoning efforts accepted by this provider. [AgentPromptReasoningEffort.AUTO] is implicit and means
   * no launch override should be added.
   */
  val supportedReasoningEfforts: Set<AgentPromptReasoningEffort>
    get() = emptySet()

  /**
   * True when the provider has a dedicated Plan-mode reasoning effort transport separate from normal reasoning effort.
   */
  val supportsPlanReasoningEffort: Boolean
    get() = false

  val supportsGenerationModelSelection: Boolean
    get() = false

  /**
   * True when provider model discovery should run even if the launch profile uses automatic generation settings.
   */
  val resolvesGenerationModelCatalogForAutoSettings: Boolean
    get() = false

  val supportsPromptLaunch: Boolean
    get() = true

  val supportedLaunchModes: Set<AgentSessionLaunchMode>
    get() = setOf(AgentSessionLaunchMode.STANDARD)

  val supportsPromptTabQueueShortcut: Boolean
    get() = false

  val suppressPromptExistingTaskSelectionHint: Boolean
    get() = false

  val editorTabActionIds: List<String>
    get() = emptyList()

  val supportsPendingEditorTabRebind: Boolean
    get() = false

  val supportsNewThreadRebind: Boolean
    get() = false

  val emitsScopedRefreshSignals: Boolean
    get() = false

  val refreshPathAfterCreateNewSession: Boolean
    get() = false

  val archiveRefreshDelayMs: Long
    get() = 0L

  val suppressArchivedThreadsDuringRefresh: Boolean
    get() = false

  val sessionSource: AgentSessionSource
  val cliMissingMessageKey: String

  val cliVisibilityPolicy: AgentSessionProviderCliVisibilityPolicy
    get() = AgentSessionProviderCliVisibilityPolicy.PROMINENT

  /**
   * Terminal-agent identifier used by `TerminalAgentResolver` to locate this provider's CLI binary.
   * The provider availability cache refreshes through [isCliAvailable], which should use the same
   * resolver path as launch-time CLI resolution so menus and launch errors agree.
   */
  val terminalAgentKey: String?
    get() = null

  val supportsArchiveThread: Boolean
    get() = false

  /**
   * Close any open chat tab before invoking [archiveThread]. Use this for providers whose archive transport resumes
   * the session non-interactively and cannot safely run while the same session is open in a terminal.
   */
  val closeOpenChatBeforeArchiveThread: Boolean
    get() = false

  /**
   * Provider-side rename implementation. Implementations should only persist or perform the provider rename and report
   * success. Agent Workbench owns session-state updates, shared thread presentation, open editor-tab presentation updates,
   * and follow-up refreshes.
   */
  val threadRenameAction: AgentThreadRenameAction?
    get() = null

  val supportsUnarchiveThread: Boolean
    get() = false

  /**
   * Resolves whether the provider's CLI binary is available, using the same shared `TerminalAgentResolver`
   * pipeline that powers terminal launches. The result reflects PATH + known-location candidates so
   * menu enable/disable matches the launch-time resolver answer.
   *
   * Synchronous UI surfaces (menu `update()` callbacks, sessions tree popup, editor-tab actions, etc.)
   * cannot suspend, and so consume the project-level provider availability cache instead of calling
   * this directly. The cache is populated by startup prewarm and refreshed from background coroutines.
   */
  suspend fun isCliAvailable(): Boolean

  suspend fun buildResumeLaunchSpec(sessionId: String): AgentSessionTerminalLaunchSpec

  suspend fun buildResumeLaunchSpec(
    sessionId: String,
    launchMode: AgentSessionLaunchMode,
  ): AgentSessionTerminalLaunchSpec = buildResumeLaunchSpec(sessionId)

  suspend fun listAvailableGenerationModels(project: Project? = null): List<AgentPromptGenerationModel> {
    return emptyList()
  }

  fun displayNameForGenerationModelId(modelId: String): String? {
    return null
  }

  suspend fun buildNewSessionLaunchSpec(mode: AgentSessionLaunchMode): AgentSessionTerminalLaunchSpec

  fun sanitizeGenerationSettings(generationSettings: AgentPromptGenerationSettings): AgentPromptGenerationSettings {
    val modelId = generationSettings.modelId
      ?.trim()
      ?.takeIf { it.isNotEmpty() }
    val reasoningEffort = generationSettings.reasoningEffort
                            .takeIf { effort -> effort == AgentPromptReasoningEffort.AUTO || effort in supportedReasoningEfforts }
                          ?: AgentPromptReasoningEffort.AUTO
    val planReasoningEffort = if (supportsPlanReasoningEffort) {
      generationSettings.planReasoningEffort
        ?.takeIf { effort -> effort == AgentPromptReasoningEffort.AUTO || effort in supportedReasoningEfforts }
      ?: generationSettings.planReasoningEffort?.let { AgentPromptReasoningEffort.AUTO }
    }
    else {
      null
    }
    return generationSettings.copy(
      modelId = modelId,
      reasoningEffort = reasoningEffort,
      planReasoningEffort = planReasoningEffort,
    )
  }

  fun applyGenerationSettings(
    baseLaunchSpec: AgentSessionTerminalLaunchSpec,
    generationSettings: AgentPromptGenerationSettings,
    initialMessagePlan: AgentInitialMessagePlan,
  ): AgentSessionTerminalLaunchSpec = baseLaunchSpec

  fun applyGenerationModelCatalog(
    baseLaunchSpec: AgentSessionTerminalLaunchSpec,
    generationSettings: AgentPromptGenerationSettings,
    generationModelCatalog: List<AgentPromptGenerationModel>,
  ): AgentSessionTerminalLaunchSpec = baseLaunchSpec

  fun buildLaunchSpecWithInitialMessage(
    baseLaunchSpec: AgentSessionTerminalLaunchSpec,
    initialMessagePlan: AgentInitialMessagePlan,
  ): AgentSessionTerminalLaunchSpec? = null

  fun buildPostStartDispatchSteps(initialMessagePlan: AgentInitialMessagePlan): List<AgentInitialMessageDispatchStep> {
    val message = initialMessagePlan.message ?: return emptyList()
    return listOf(
      AgentInitialMessageDispatchStep(
        text = message,
        timeoutPolicy = initialMessagePlan.timeoutPolicy,
      )
    )
  }

  suspend fun archiveThread(path: String, threadId: String): Boolean = false

  suspend fun unarchiveThread(path: String, threadId: String): Boolean = false

  fun buildInitialMessagePlan(request: AgentPromptInitialMessageRequest): AgentInitialMessagePlan

  /**
   * True when [buildInitialMessagePlan] depends on data refreshed by [isCliAvailable].
   */
  val requiresCliAvailabilityForInitialMessagePlan: Boolean
    get() = false

  suspend fun listReusablePromptSourceEntries(projectPath: String): List<AgentPromptReusableSourceEntry> {
    return emptyList()
  }

  fun onConversationOpened() {
  }

  fun recordNewSession(path: String, threadId: String, title: String, createdAtMs: Long) {
  }

  val supportsTerminalRestoreContext: Boolean
    get() = false

  fun readTerminalRestoreContext(path: String, threadId: String): AgentSessionTerminalRestoreContext? = null

  fun recordTerminalWorkingDirectory(path: String, threadId: String, workingDirectory: String) {
  }

  fun recordTerminalSessionClosed(path: String, threadId: String) {
  }

  /**
   * CLI-arg marker that distinguishes a YOLO-mode launch from a standard one for this provider.
   * The default [resolvePendingSessionMetadata] implementation looks for this token in
   * [AgentSessionTerminalLaunchSpec.command] to label the launch mode. `null` (the default)
   * means YOLO is not distinguishable from the launch spec — every pending session is reported
   * as `"standard"`.
   */
  val pendingSessionLaunchYoloMarker: String?
    get() = null

  fun resolvePendingSessionMetadata(
    identity: String,
    launchSpec: AgentSessionTerminalLaunchSpec,
  ): AgentPendingSessionMetadata? {
    val parsed = parseAgentThreadIdentity(identity) ?: return null
    if (!isAgentSessionPendingThreadId(parsed.threadId)) return null
    if (parsed.providerId != provider.value) return null
    val yoloMarker = pendingSessionLaunchYoloMarker
    val launchMode = if (yoloMarker != null && yoloMarker in launchSpec.command) "yolo" else "standard"
    return AgentPendingSessionMetadata(createdAtMs = System.currentTimeMillis(), launchMode = launchMode)
  }

  fun createToolWindowNorthComponent(project: Project): JComponent? = null

  fun isCliMissingError(throwable: Throwable): Boolean = false
}

data class AgentSessionTerminalLaunchSpec(
  @JvmField val command: List<String>,
  @JvmField val envVariables: Map<String, String> = emptyMap(),
  @JvmField val workingDirectory: String? = null,
  /**
   * Uses the IDE Terminal default shell instead of an explicit non-shell command.
   * The embedded Agent Chat terminal passes no `shellCommand` to the Terminal tab builder in this mode.
   */
  @JvmField val useTerminalDefaultShell: Boolean = false,
  /**
   * When set, this session targets a Docker container managed by `ContainerSessionManager`.
   * The ij-proxy MCP server routes file I/O and bash tool calls through the container
   * instead of the local filesystem. Semantic tools (search_symbol, get_file_problems)
   * fall back to the host IDE index.
   *
   * The ID is also passed to Claude Code as `AGENT_CONTAINER_SESSION_ID` env var.
   */
  @JvmField val containerSessionId: String? = null,
  /**
   * Provider-allocated concrete session id for new-session launches.
   * When set, Agent Chat opens the tab with the concrete identity immediately instead of a synthetic `new-*` id.
   */
  @JvmField val preallocatedSessionId: String? = null,
)

data class AgentSessionTerminalRestoreContext(
  @JvmField val workingDirectory: String? = null,
)
