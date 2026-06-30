// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ai.agent.opencode.sessions

import com.intellij.platform.ai.agent.common.icons.AgentWorkbenchCommonIcons
import com.intellij.platform.ai.agent.core.session.AgentSessionLaunchMode
import com.intellij.agent.workbench.prompt.core.AgentPromptContextEnvelopeFormatter
import com.intellij.agent.workbench.prompt.core.AgentPromptGenerationModel
import com.intellij.agent.workbench.prompt.core.AgentPromptGenerationSettings
import com.intellij.agent.workbench.prompt.core.AgentPromptInitialMessageRequest
import com.intellij.agent.workbench.prompt.core.AgentPromptReasoningEffort
import com.intellij.platform.ai.agent.sessions.core.launch.insertArgumentsBefore
import com.intellij.platform.ai.agent.sessions.core.launch.removeOptions
import com.intellij.platform.ai.agent.sessions.core.providers.AgentInitialMessagePlan
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionProviderCliVisibilityPolicy
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionProviderDescriptor
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionSource
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionTerminalLaunchSpec
import com.intellij.platform.ai.agent.sessions.core.providers.AgentThreadRenameAction
import com.intellij.openapi.project.Project
import javax.swing.Icon

internal class OpenCodeAgentSessionProviderDescriptor(
  private val sessionStore: OpenCodeSessionStore = OpenCodeSessionStore(),
  override val sessionSource: AgentSessionSource = OpenCodeSessionSource(sessionStore),
  private val executableResolver: suspend () -> String = OpenCodeCliSupport::resolveExecutableOrDefaultViaTerminalResolver,
  private val cliAvailableProbe: suspend () -> Boolean = { OpenCodeCliSupport.findExecutableViaTerminalResolver() != null },
) : AgentSessionProviderDescriptor {
  override val provider = OPENCODE_AGENT_SESSION_PROVIDER

  override val displayPriority: Int
    get() = 4

  override val displayNameKey: String
    get() = "toolwindow.provider.opencode"

  override val newSessionLabelKey: String
    get() = "toolwindow.action.new.session.opencode"

  override val icon: Icon
    get() = AgentWorkbenchCommonIcons.Opencode

  override val monochromeIcon: Icon
    get() = AgentWorkbenchCommonIcons.OpencodeGray

  override val supportedLaunchModes: Set<AgentSessionLaunchMode>
    get() = setOf(AgentSessionLaunchMode.STANDARD)

  override val supportedReasoningEfforts: Set<AgentPromptReasoningEffort>
    get() = setOf(
      AgentPromptReasoningEffort.LOW,
      AgentPromptReasoningEffort.MEDIUM,
      AgentPromptReasoningEffort.HIGH,
      AgentPromptReasoningEffort.MAX,
    )

  override val supportsGenerationModelSelection: Boolean
    get() = true

  override val cliMissingMessageKey: String
    get() = "toolwindow.error.opencode.cli"

  override val cliVisibilityPolicy: AgentSessionProviderCliVisibilityPolicy
    get() = AgentSessionProviderCliVisibilityPolicy.DISCOVER_WHEN_AVAILABLE

  override val terminalAgentKey: String
    get() = OpenCodeCliSupport.OPENCODE_TERMINAL_AGENT_KEY

  override val supportsArchiveThread: Boolean
    get() = true

  override val supportsUnarchiveThread: Boolean
    get() = true

  override val threadRenameAction: AgentThreadRenameAction = { path, threadId, normalizedName ->
    sessionStore.renameThread(path = path, threadId = threadId, normalizedName = normalizedName)
  }

  override suspend fun isCliAvailable(): Boolean = cliAvailableProbe()

  override suspend fun archiveThread(path: String, threadId: String): Boolean {
    return sessionStore.archiveThread(path = path, threadId = threadId)
  }

  override suspend fun unarchiveThread(path: String, threadId: String): Boolean {
    return sessionStore.unarchiveThread(path = path, threadId = threadId)
  }

  override suspend fun buildResumeLaunchSpec(sessionId: String): AgentSessionTerminalLaunchSpec {
    return buildResumeLaunchSpec(sessionId, AgentSessionLaunchMode.STANDARD)
  }

  override suspend fun buildResumeLaunchSpec(
    sessionId: String,
    launchMode: AgentSessionLaunchMode,
  ): AgentSessionTerminalLaunchSpec {
    return AgentSessionTerminalLaunchSpec(command = listOf(executableResolver(), OPENCODE_SESSION_FLAG, sessionId))
  }

  override suspend fun buildNewSessionLaunchSpec(mode: AgentSessionLaunchMode): AgentSessionTerminalLaunchSpec {
    return AgentSessionTerminalLaunchSpec(command = listOf(executableResolver()))
  }

  override suspend fun listAvailableGenerationModels(project: Project?): List<AgentPromptGenerationModel> {
    val projectPath = project?.basePath?.takeIf { it.isNotBlank() }
                      ?: System.getProperty("user.home")?.takeIf { it.isNotBlank() }
                      ?: return emptyList()
    return OpenCodeAcpGenerationModelCatalog.listAvailableGenerationModels(executableResolver(), projectPath)
  }

  override fun applyGenerationSettings(
    baseLaunchSpec: AgentSessionTerminalLaunchSpec,
    generationSettings: AgentPromptGenerationSettings,
    initialMessagePlan: AgentInitialMessagePlan,
  ): AgentSessionTerminalLaunchSpec {
    val settings = sanitizeGenerationSettings(generationSettings)
    val generationArgs = buildOpenCodeGenerationArgs(settings)
    if (generationArgs.isEmpty()) {
      return baseLaunchSpec
    }
    return baseLaunchSpec.copy(
      command = replaceOpenCodeGenerationArgs(baseLaunchSpec.command, generationArgs),
    )
  }

  override fun buildLaunchSpecWithInitialMessage(
    baseLaunchSpec: AgentSessionTerminalLaunchSpec,
    initialMessagePlan: AgentInitialMessagePlan,
  ): AgentSessionTerminalLaunchSpec {
    val message = initialMessagePlan.message ?: return baseLaunchSpec
    return baseLaunchSpec.copy(
      command = baseLaunchSpec.command + listOf(OPENCODE_PROMPT_FLAG, message),
    )
  }

  override fun buildInitialMessagePlan(request: AgentPromptInitialMessageRequest): AgentInitialMessagePlan {
    val message = AgentPromptContextEnvelopeFormatter.composeInitialMessage(request)
      .trim()
      .takeIf { it.isNotEmpty() }
    return AgentInitialMessagePlan(message = message)
  }

}

private fun buildOpenCodeGenerationArgs(settings: AgentPromptGenerationSettings): List<String> {
  val args = mutableListOf<String>()
  settings.modelId?.let { modelId -> args.addAll(listOf(OPENCODE_MODEL_FLAG, modelId)) }
  val effort = settings.reasoningEffort
  if (effort != AgentPromptReasoningEffort.AUTO) {
    args.addAll(listOf(OPENCODE_VARIANT_FLAG, effort.opencodeCliValue()))
  }
  return args
}

private fun replaceOpenCodeGenerationArgs(command: List<String>, args: List<String>): List<String> {
  val commandWithoutGenerationArgs = removeOptions(command, OPENCODE_GENERATION_FLAGS, beforeTokens = setOf(OPENCODE_PROMPT_FLAG))
  return insertArgumentsBefore(commandWithoutGenerationArgs, args, beforeTokens = setOf(OPENCODE_PROMPT_FLAG))
}

private fun AgentPromptReasoningEffort.opencodeCliValue(): String {
  return name.lowercase()
}

internal const val OPENCODE_MCP_URL_ENVIRONMENT_VARIABLE: String = "JETBRAINS_MCP_URL"

private const val OPENCODE_SESSION_FLAG: String = "--session"
private const val OPENCODE_PROMPT_FLAG: String = "--prompt"
private const val OPENCODE_MODEL_FLAG: String = "--model"
private const val OPENCODE_VARIANT_FLAG: String = "--variant"
private val OPENCODE_GENERATION_FLAGS: Set<String> = setOf(OPENCODE_MODEL_FLAG, OPENCODE_VARIANT_FLAG)
