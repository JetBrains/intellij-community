// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.codex.sessions

import com.intellij.agent.workbench.codex.common.CodexCliNotFoundException
import com.intellij.agent.workbench.codex.common.CodexCliUtils
import com.intellij.agent.workbench.codex.sessions.backend.appserver.SharedCodexAppServerService
import com.intellij.agent.workbench.common.AgentWorkbenchActionIds
import com.intellij.agent.workbench.common.icons.AgentWorkbenchCommonIcons
import com.intellij.agent.workbench.common.session.AgentSessionLaunchMode
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.prompt.core.AgentPromptGenerationModel
import com.intellij.agent.workbench.prompt.core.AgentPromptGenerationModelGroup
import com.intellij.agent.workbench.prompt.core.AgentPromptGenerationSettings
import com.intellij.agent.workbench.prompt.core.AgentPromptInitialMessageRequest
import com.intellij.agent.workbench.prompt.core.AgentPromptReasoningEffort
import com.intellij.agent.workbench.prompt.core.AgentPromptReusableSourceEntry
import com.intellij.agent.workbench.prompt.core.AgentPromptReusableSourceKind
import com.intellij.agent.workbench.prompt.core.withGroup
import com.intellij.agent.workbench.sessions.core.providers.AGENT_PROMPT_PROVIDER_PLAN_MODE_OPTION
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessageDispatchCompletionPolicy
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessageDispatchStep
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessageMode
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessagePlan
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessageStartupPolicy
import com.intellij.agent.workbench.sessions.core.providers.AgentPromptProviderOption
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderDescriptor
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSource
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionTerminalLaunchSpec
import com.intellij.agent.workbench.sessions.core.providers.AgentThreadRenameAction
import com.intellij.agent.workbench.sessions.core.providers.buildPlanModeInitialMessagePlan
import com.intellij.agent.workbench.sessions.core.providers.buildTerminalPlanModePostStartDispatchSteps
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.project.Project
import java.nio.file.Path
import javax.swing.Icon

internal class CodexAgentSessionProviderDescriptor(
  override val sessionSource: AgentSessionSource = CodexSessionSource(),
  private val threadMutationBackend: CodexThreadMutationBackend = SharedServiceCodexThreadMutationBackend,
  /**
   * Resolves the `codex` executable for terminal launch specs. Defaults to
   * [CodexCliUtils.resolveExecutableOrDefaultViaTerminalResolver], which delegates to the shared
   * `TerminalAgentResolver` so agent-workbench launches and the terminal "Run AI agent" gutter pick the
   * same `codex` binary; falls back to the bare `codex` command when the binary cannot be located so
   * the existing `cliMissingMessageKey` UI guard remains in charge of explaining the missing CLI. Tests
   * inject a fixed resolver so assertions remain deterministic regardless of the host's PATH.
   */
  private val executableResolver: suspend () -> String = CodexCliUtils::resolveExecutableOrDefaultViaTerminalResolver,
  private val cliAvailableProbe: suspend () -> Boolean = { CodexCliUtils.findExecutableViaTerminalResolver() != null },
) : AgentSessionProviderDescriptor {
  override val provider: AgentSessionProvider
    get() = AgentSessionProvider.CODEX

  override val displayPriority: Int
    get() = 0

  override val displayNameKey: String
    get() = "toolwindow.provider.codex"

  override val newSessionLabelKey: String
    get() = "toolwindow.action.new.session.codex"

  override val yoloSessionLabelKey: String
    get() = "toolwindow.action.new.session.codex.yolo"

  override val yoloSessionModeLabelKey: String
    get() = "toolwindow.action.new.session.codex.yolo.mode"

  override val icon: Icon
    get() = AgentWorkbenchCommonIcons.Codex

  override val monochromeIcon: Icon
    get() = AgentWorkbenchCommonIcons.CodexGray

  override val supportedLaunchModes: Set<AgentSessionLaunchMode>
    get() = setOf(AgentSessionLaunchMode.STANDARD, AgentSessionLaunchMode.YOLO)

  override val supportsPromptTabQueueShortcut: Boolean
    get() = true

  override val suppressPromptExistingTaskSelectionHint: Boolean
    get() = true

  override val promptOptions: List<AgentPromptProviderOption>
    get() = listOf(AGENT_PROMPT_PROVIDER_PLAN_MODE_OPTION)

  override val supportedReasoningEfforts: Set<AgentPromptReasoningEffort>
    get() = setOf(
      AgentPromptReasoningEffort.LOW,
      AgentPromptReasoningEffort.MEDIUM,
      AgentPromptReasoningEffort.HIGH,
      AgentPromptReasoningEffort.XHIGH,
    )

  override val supportsPlanReasoningEffort: Boolean
    get() = true

  override val supportsGenerationModelSelection: Boolean
    get() = true

  override val editorTabActionIds: List<String>
    get() = listOf(AgentWorkbenchActionIds.Sessions.BIND_PENDING_AGENT_THREAD_FROM_EDITOR_TAB)

  override val supportsPendingEditorTabRebind: Boolean
    get() = true

  override val supportsNewThreadRebind: Boolean
    get() = true

  override val emitsScopedRefreshSignals: Boolean
    get() = true

  override val refreshPathAfterCreateNewSession: Boolean
    get() = true

  override val archiveRefreshDelayMs: Long
    get() = 1_000L

  override val suppressArchivedThreadsDuringRefresh: Boolean
    get() = true

  override val cliMissingMessageKey: String
    get() = "toolwindow.error.cli"

  override val terminalAgentKey: String
    get() = CodexCliUtils.CODEX_TERMINAL_AGENT_KEY

  override val supportsArchiveThread: Boolean
    get() = true

  override val supportsUnarchiveThread: Boolean
    get() = true

  override val pendingSessionLaunchYoloMarker: String
    get() = "--yolo"

  override val threadRenameAction: AgentThreadRenameAction = { path, threadId, normalizedName ->
    threadMutationBackend.setThreadName(path, threadId, normalizedName)
    true
  }

  override suspend fun isCliAvailable(): Boolean = cliAvailableProbe()

  override suspend fun buildResumeLaunchSpec(sessionId: String): AgentSessionTerminalLaunchSpec {
    return buildResumeLaunchSpec(sessionId, AgentSessionLaunchMode.STANDARD)
  }

  override suspend fun buildResumeLaunchSpec(
    sessionId: String,
    launchMode: AgentSessionLaunchMode,
  ): AgentSessionTerminalLaunchSpec {
    val executable = executableResolver()
    val command = if (launchMode == AgentSessionLaunchMode.YOLO) {
      buildCodexBaseCommand(executable) + listOf("--yolo", "resume", sessionId)
    }
    else {
      buildCodexBaseCommand(executable) + listOf("resume", sessionId)
    }
    return AgentSessionTerminalLaunchSpec(
      command = command,
    )
  }

  override suspend fun buildNewSessionLaunchSpec(mode: AgentSessionLaunchMode): AgentSessionTerminalLaunchSpec {
    val executable = executableResolver()
    val command = if (mode == AgentSessionLaunchMode.YOLO) {
      buildCodexBaseCommand(executable) + "--yolo"
    }
    else {
      buildCodexBaseCommand(executable)
    }
    return AgentSessionTerminalLaunchSpec(command = command)
  }

  override suspend fun listAvailableGenerationModels(project: Project?): List<AgentPromptGenerationModel> {
    val service = serviceAsync<SharedCodexAppServerService>()
    return runCatching { service.listModels() }
      .getOrDefault(emptyList())
      .asSequence()
      .filterNot { model -> model.hidden }
      .map { model ->
        AgentPromptGenerationModel(
          id = model.id,
          displayName = model.displayName ?: model.id,
          supportedReasoningEfforts = model.supportedReasoningEfforts.mapNotNullTo(LinkedHashSet(), ::toPromptReasoningEffort),
          defaultReasoningEffort = toPromptReasoningEffort(model.defaultReasoningEffort),
          isDefault = model.isDefault,
        ).withGroup(AgentPromptGenerationModelGroup.OPENAI)
      }
      .toList()
  }

  override fun applyGenerationSettings(
    baseLaunchSpec: AgentSessionTerminalLaunchSpec,
    generationSettings: AgentPromptGenerationSettings,
    initialMessagePlan: AgentInitialMessagePlan,
  ): AgentSessionTerminalLaunchSpec {
    val settings = sanitizeGenerationSettings(generationSettings)
    val generationArgs = buildCodexGenerationArgs(
      settings = settings,
      planMode = initialMessagePlan.mode == AgentInitialMessageMode.PLAN,
    )
    if (generationArgs.isEmpty()) {
      return baseLaunchSpec
    }
    return baseLaunchSpec.copy(
      command = insertCodexGenerationArgs(baseLaunchSpec.command, generationArgs),
    )
  }

  override fun buildLaunchSpecWithInitialMessage(
    baseLaunchSpec: AgentSessionTerminalLaunchSpec,
    initialMessagePlan: AgentInitialMessagePlan,
  ): AgentSessionTerminalLaunchSpec? {
    val message = initialMessagePlan.message ?: return null
    if (initialMessagePlan.startupPolicy != AgentInitialMessageStartupPolicy.TRY_STARTUP_COMMAND) {
      return null
    }
    return baseLaunchSpec.copy(command = baseLaunchSpec.command + listOf("--", message))
  }

  override fun buildInitialMessagePlan(request: AgentPromptInitialMessageRequest): AgentInitialMessagePlan {
    return buildPlanModeInitialMessagePlan(
      request = request,
      startupPolicyWhenPlanModeEnabled = AgentInitialMessageStartupPolicy.POST_START_ONLY,
    )
  }

  override suspend fun listReusablePromptSourceEntries(projectPath: String): List<AgentPromptReusableSourceEntry> {
    val normalizedPath = projectPath.takeIf(String::isNotBlank) ?: return emptyList()
    val service = serviceAsync<SharedCodexAppServerService>()
    return runCatching { service.listSkills(Path.of(normalizedPath)) }
      .getOrDefault(emptyList())
      .asSequence()
      .filter { skill -> skill.enabled }
      .map { skill ->
        AgentPromptReusableSourceEntry(
          id = "codex:skill:${skill.path ?: skill.name}",
          label = skill.displayName ?: skill.name,
          insertText = "$" + skill.name + " ",
          kind = AgentPromptReusableSourceKind.SKILL,
          provider = AgentSessionProvider.CODEX,
          description = skill.shortDescription ?: skill.description ?: skill.defaultPrompt,
          sourcePath = skill.path,
        )
      }
      .toList()
  }

  override fun buildPostStartDispatchSteps(initialMessagePlan: AgentInitialMessagePlan): List<AgentInitialMessageDispatchStep> {
    if (initialMessagePlan.mode != AgentInitialMessageMode.PLAN) {
      return super.buildPostStartDispatchSteps(initialMessagePlan)
    }

    return buildTerminalPlanModePostStartDispatchSteps(
      initialMessagePlan = initialMessagePlan,
      completionPolicy = AgentInitialMessageDispatchCompletionPolicy.RETRY_ON_CODEX_PLAN_BUSY,
    )
  }

  override suspend fun archiveThread(path: String, threadId: String): Boolean {
    threadMutationBackend.archiveThread(path, threadId)
    return true
  }

  override suspend fun unarchiveThread(path: String, threadId: String): Boolean {
    threadMutationBackend.unarchiveThread(path, threadId)
    return true
  }

  override fun isCliMissingError(throwable: Throwable): Boolean {
    return throwable is CodexCliNotFoundException
  }
}

internal interface CodexThreadMutationBackend {
  suspend fun archiveThread(path: String, threadId: String)

  suspend fun unarchiveThread(path: String, threadId: String)

  suspend fun setThreadName(path: String, threadId: String, name: String)
}

private object SharedServiceCodexThreadMutationBackend : CodexThreadMutationBackend {
  override suspend fun archiveThread(path: String, threadId: String) {
    serviceAsync<SharedCodexAppServerService>().archiveThread(threadId)
  }

  override suspend fun unarchiveThread(path: String, threadId: String) {
    serviceAsync<SharedCodexAppServerService>().unarchiveThread(threadId)
  }

  override suspend fun setThreadName(path: String, threadId: String, name: String) {
    serviceAsync<SharedCodexAppServerService>().setThreadName(threadId, name)
  }
}

private fun buildCodexBaseCommand(executable: String): List<String> {
  return listOf(
    executable,
    "-c",
    CODEX_AUTO_UPDATE_CONFIG,
    "-c",
    CODEX_TERMINAL_TITLE_CONFIG,
  )
}

private fun buildCodexGenerationArgs(
  settings: AgentPromptGenerationSettings,
  planMode: Boolean,
): List<String> {
  val args = mutableListOf<String>()
  settings.modelId?.let { modelId -> args.addAll(listOf("--model", modelId)) }
  val effort = settings.reasoningEffort
  if (effort != AgentPromptReasoningEffort.AUTO) {
    val effortValue = effort.codexConfigValue()
    args.addAll(listOf("-c", "model_reasoning_effort=\"$effortValue\""))
  }
  val planEffort = settings.planReasoningEffort ?: effort
  if (planMode && planEffort != AgentPromptReasoningEffort.AUTO) {
    args.addAll(listOf("-c", "plan_mode_reasoning_effort=\"${planEffort.codexConfigValue()}\""))
  }
  return args
}

private fun insertCodexGenerationArgs(command: List<String>, args: List<String>): List<String> {
  val promptSeparatorIndex = command.indexOf("--").takeIf { it >= 0 } ?: command.size
  return command.toMutableList().apply {
    addAll(promptSeparatorIndex, args)
  }
}

private fun AgentPromptReasoningEffort.codexConfigValue(): String {
  return name.lowercase()
}

private fun toPromptReasoningEffort(value: String?): AgentPromptReasoningEffort? {
  return when (value.normalizeCodexToken()) {
    "low" -> AgentPromptReasoningEffort.LOW
    "medium" -> AgentPromptReasoningEffort.MEDIUM
    "high" -> AgentPromptReasoningEffort.HIGH
    "xhigh" -> AgentPromptReasoningEffort.XHIGH
    "max" -> AgentPromptReasoningEffort.MAX
    else -> null
  }
}

private fun String?.normalizeCodexToken(): String {
  return this
    ?.trim()
    ?.lowercase()
    ?.replace("_", "")
    ?.replace("-", "")
    ?.replace(" ", "")
    .orEmpty()
}

private const val CODEX_AUTO_UPDATE_CONFIG: String = "check_for_update_on_startup=false"

// The dedicated thread-id title item is the stable UUID signal. The thread item keeps the human title/fallback visible.
private const val CODEX_TERMINAL_TITLE_CONFIG: String = "tui.terminal_title=[\"thread-id\",\"thread\"]"
