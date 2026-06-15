// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.claude.sessions

import com.intellij.agent.workbench.common.AgentWorkbenchActionIds
import com.intellij.agent.workbench.common.icons.AgentWorkbenchCommonIcons
import com.intellij.agent.workbench.common.session.AgentSessionLaunchMode
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.common.session.isClaudeMenuCommandPrompt
import com.intellij.agent.workbench.prompt.core.AgentPromptGenerationSettings
import com.intellij.agent.workbench.prompt.core.AgentPromptInitialMessageRequest
import com.intellij.agent.workbench.prompt.core.AgentPromptReasoningEffort
import com.intellij.agent.workbench.sessions.core.providers.AGENT_PROMPT_PROVIDER_PLAN_MODE_OPTION
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessageMode
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessagePlan
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessageStartupPolicy
import com.intellij.agent.workbench.sessions.core.providers.AgentPromptProviderOption
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderDescriptor
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSource
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionTerminalLaunchSpec
import com.intellij.agent.workbench.sessions.core.providers.AgentThreadRenameAction
import com.intellij.agent.workbench.sessions.core.providers.buildPlanModeInitialMessagePlan
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import java.util.UUID
import javax.swing.Icon
import javax.swing.JComponent

internal class ClaudeAgentSessionProviderDescriptor(
  private val backend: ClaudeSessionBackend = createDefaultClaudeSessionBackend(),
  override val sessionSource: AgentSessionSource = ClaudeSessionSource(backend = backend),
  /**
   * Resolves the `claude` executable for terminal launch specs and rename PTY launches. Defaults to
   * [ClaudeCliSupport.resolveExecutableOrDefaultViaTerminalResolver], which delegates to the shared
   * `TerminalAgentResolver` so agent-workbench launches and the terminal "Run AI agent" gutter pick the
   * same `claude` binary; falls back to the bare `claude` command when the binary cannot be located so
   * the existing `cliMissingMessageKey` UI guard remains in charge of explaining the missing CLI. Tests
   * inject a fixed resolver so assertions remain deterministic regardless of the host's PATH.
   */
  private val executableResolver: suspend () -> String = ClaudeCliSupport::resolveExecutableOrDefaultViaTerminalResolver,
  private val renameEngine: ClaudeThreadRenameEngine = ClaudeOpenTabAwareThreadRenameEngine(
    backend = backend,
    fallbackEngine = PtyClaudeThreadRenameEngine(backend = backend, executableResolver = executableResolver),
    executableResolver = executableResolver,
  ),
  private val cliAvailableProbe: suspend () -> Boolean = { ClaudeCliSupport.findExecutableViaTerminalResolver() != null },
) : AgentSessionProviderDescriptor {
  override val provider: AgentSessionProvider
    get() = AgentSessionProvider.CLAUDE

  override val displayPriority: Int
    get() = 1

  override val displayNameKey: String
    get() = "toolwindow.provider.claude"

  override val cliDisplayNameKey: String
    get() = "toolwindow.provider.cli.claude"

  override val cliDisplayNameFallback: String
    get() = "Claude Code"

  override val newSessionLabelKey: String
    get() = "toolwindow.action.new.session.claude"

  override val yoloSessionLabelKey: String
    get() = "toolwindow.action.new.session.claude.yolo"

  override val yoloSessionModeLabelKey: String
    get() = "toolwindow.action.new.session.claude.yolo.mode"

  override val icon: Icon
    get() = AgentWorkbenchCommonIcons.Claude

  override val monochromeIcon: Icon
    get() = AgentWorkbenchCommonIcons.ClaudeGray

  override val supportedLaunchModes: Set<AgentSessionLaunchMode>
    get() = setOf(AgentSessionLaunchMode.STANDARD, AgentSessionLaunchMode.YOLO)

  override val promptOptions: List<AgentPromptProviderOption>
    get() = listOf(AGENT_PROMPT_PROVIDER_PLAN_MODE_OPTION)

  override val supportedReasoningEfforts: Set<AgentPromptReasoningEffort>
    get() = setOf(
      AgentPromptReasoningEffort.LOW,
      AgentPromptReasoningEffort.MEDIUM,
      AgentPromptReasoningEffort.HIGH,
      AgentPromptReasoningEffort.XHIGH,
      AgentPromptReasoningEffort.MAX,
    )

  override val editorTabActionIds: List<String>
    get() = listOf(AgentWorkbenchActionIds.Sessions.BIND_PENDING_AGENT_THREAD_FROM_EDITOR_TAB)

  override val supportsPendingEditorTabRebind: Boolean
    get() = true

  override val emitsScopedRefreshSignals: Boolean
    get() = true

  override val refreshPathAfterCreateNewSession: Boolean
    get() = true

  override val archiveRefreshDelayMs: Long
    get() = 1_000L

  override val suppressArchivedThreadsDuringRefresh: Boolean
    get() = true

  override val supportsArchiveThread: Boolean
    get() = true

  override val closeOpenChatBeforeArchiveThread: Boolean
    get() = true

  override val supportsUnarchiveThread: Boolean
    get() = true

  override val pendingSessionLaunchYoloMarker: String
    get() = "--dangerously-skip-permissions"

  override val threadRenameAction: AgentThreadRenameAction = { path, threadId, normalizedName ->
    renameEngine.rename(path = path, threadId = threadId, newTitle = normalizedName)
  }

  override val cliMissingMessageKey: String
    get() = "toolwindow.error.claude.cli"

  override val terminalAgentKey: String
    get() = ClaudeCliSupport.CLAUDE_TERMINAL_AGENT_KEY

  override suspend fun isCliAvailable(): Boolean = cliAvailableProbe()

  override suspend fun buildResumeLaunchSpec(sessionId: String): AgentSessionTerminalLaunchSpec {
    return buildResumeLaunchSpec(sessionId, AgentSessionLaunchMode.STANDARD)
  }

  override suspend fun buildResumeLaunchSpec(
    sessionId: String,
    launchMode: AgentSessionLaunchMode,
  ): AgentSessionTerminalLaunchSpec {
    return buildClaudeResumeLaunchSpec(
      sessionId = sessionId,
      executable = executableResolver(),
      launchMode = launchMode,
    )
  }

  override suspend fun buildNewSessionLaunchSpec(mode: AgentSessionLaunchMode): AgentSessionTerminalLaunchSpec {
    return buildClaudeNewSessionLaunchSpec(mode, executableResolver())
  }

  override fun applyGenerationSettings(
    baseLaunchSpec: AgentSessionTerminalLaunchSpec,
    generationSettings: AgentPromptGenerationSettings,
    initialMessagePlan: AgentInitialMessagePlan,
  ): AgentSessionTerminalLaunchSpec {
    val settings = sanitizeGenerationSettings(generationSettings)
    val effort = settings.reasoningEffort
    if (effort == AgentPromptReasoningEffort.AUTO) {
      return baseLaunchSpec
    }
    return baseLaunchSpec.copy(command = replaceOrAddEffort(baseLaunchSpec.command, effort.claudeCliValue()))
  }

  override fun buildLaunchSpecWithInitialMessage(
    baseLaunchSpec: AgentSessionTerminalLaunchSpec,
    initialMessagePlan: AgentInitialMessagePlan,
  ): AgentSessionTerminalLaunchSpec {
    return buildClaudeLaunchSpecWithInitialMessage(baseLaunchSpec, initialMessagePlan)
  }

  override fun buildInitialMessagePlan(request: AgentPromptInitialMessageRequest): AgentInitialMessagePlan {
    if (request.prompt.isClaudeMenuCommandPrompt()) {
      return AgentInitialMessagePlan(
        message = request.prompt.trim(),
        startupPolicy = AgentInitialMessageStartupPolicy.POST_START_ONLY,
      )
    }

    return buildPlanModeInitialMessagePlan(
      request = request,
      startupPolicyWhenPlanModeEnabled = AgentInitialMessageStartupPolicy.TRY_STARTUP_COMMAND,
    )
  }

  override fun onConversationOpened() {
    service<ClaudeQuotaHintStateService>().markEligible()
  }

  override fun createToolWindowNorthComponent(project: Project): JComponent {
    return ClaudeQuotaHintBanner()
  }

  override suspend fun archiveThread(path: String, threadId: String): Boolean {
    return renameEngine.archiveThread(path, threadId)
  }

  override suspend fun unarchiveThread(path: String, threadId: String): Boolean {
    return renameEngine.unarchiveThread(path, threadId)
  }
}

internal fun replaceOrAddPermissionMode(command: List<String>, mode: String): List<String> {
  val result = command.toMutableList()
  val index = result.indexOf(PERMISSION_MODE_FLAG)
  if (index >= 0 && index + 1 < result.size) {
    result[index + 1] = mode
  }
  else {
    result.addAll(listOf(PERMISSION_MODE_FLAG, mode))
  }
  return result
}

internal fun replaceOrAddEffort(command: List<String>, effort: String): List<String> {
  val result = command.toMutableList()
  val index = result.indexOf(EFFORT_FLAG)
  if (index >= 0 && index + 1 < result.size) {
    result[index + 1] = effort
    return result
  }

  val promptSeparatorIndex = result.indexOf("--").takeIf { it >= 0 } ?: result.size
  result.addAll(promptSeparatorIndex, listOf(EFFORT_FLAG, effort))
  return result
}

private fun AgentPromptReasoningEffort.claudeCliValue(): String {
  return name.lowercase()
}

internal fun buildClaudeResumeLaunchSpec(
  sessionId: String,
  executable: String = ClaudeCliSupport.CLAUDE_COMMAND,
  launchMode: AgentSessionLaunchMode = AgentSessionLaunchMode.STANDARD,
): AgentSessionTerminalLaunchSpec {
  return AgentSessionTerminalLaunchSpec(
    command = ClaudeCliSupport.buildResumeCommand(
      sessionId = sessionId,
      yolo = launchMode == AgentSessionLaunchMode.YOLO,
      executable = executable,
    ),
    envVariables = mapOf(CLAUDE_DISABLE_AUTO_UPDATER_ENV to CLAUDE_DISABLE_AUTO_UPDATER_VALUE),
  )
}

internal fun buildClaudeNewSessionLaunchSpec(
  mode: AgentSessionLaunchMode,
  executable: String = ClaudeCliSupport.CLAUDE_COMMAND,
): AgentSessionTerminalLaunchSpec {
  val sessionId = UUID.randomUUID().toString()
  return AgentSessionTerminalLaunchSpec(
    command = ClaudeCliSupport.buildNewSessionCommand(
      yolo = mode == AgentSessionLaunchMode.YOLO,
      sessionId = sessionId,
      executable = executable,
    ),
    envVariables = mapOf(CLAUDE_DISABLE_AUTO_UPDATER_ENV to CLAUDE_DISABLE_AUTO_UPDATER_VALUE),
    preallocatedSessionId = sessionId,
  )
}

internal fun buildClaudeLaunchSpecWithInitialMessage(
  baseLaunchSpec: AgentSessionTerminalLaunchSpec,
  initialMessagePlan: AgentInitialMessagePlan,
): AgentSessionTerminalLaunchSpec {
  val command = if (initialMessagePlan.mode == AgentInitialMessageMode.PLAN) {
    replaceOrAddPermissionMode(baseLaunchSpec.command, PERMISSION_MODE_PLAN)
  }
  else {
    baseLaunchSpec.command
  }
  val effectivePrompt = initialMessagePlan.message ?: return baseLaunchSpec.copy(command = command)
  return baseLaunchSpec.copy(command = command + listOf("--", effectivePrompt))
}

private const val CLAUDE_DISABLE_AUTO_UPDATER_ENV: String = "DISABLE_AUTOUPDATER"
private const val CLAUDE_DISABLE_AUTO_UPDATER_VALUE: String = "1"
