// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ai.agent.junie.sessions

import com.intellij.platform.ai.agent.common.icons.AgentWorkbenchCommonIcons
import com.intellij.platform.ai.agent.core.session.AgentSessionLaunchMode
import com.intellij.platform.ai.agent.core.session.AgentSessionProvider
import com.intellij.platform.ai.agent.junie.common.BRAVE_FLAG
import com.intellij.platform.ai.agent.junie.common.JunieCliInfo
import com.intellij.platform.ai.agent.junie.common.JunieCliSupport
import com.intellij.agent.workbench.prompt.core.AgentPromptGenerationSettings
import com.intellij.agent.workbench.prompt.core.AgentPromptGenerationModel
import com.intellij.agent.workbench.prompt.core.AgentPromptInitialMessageRequest
import com.intellij.agent.workbench.prompt.core.AgentPromptReasoningEffort
import com.intellij.platform.ai.agent.sessions.core.launch.insertArgumentsBefore
import com.intellij.platform.ai.agent.sessions.core.launch.removeOptions
import com.intellij.platform.ai.agent.sessions.core.providers.AGENT_PROMPT_PROVIDER_PLAN_MODE_OPTION
import com.intellij.platform.ai.agent.sessions.core.providers.AgentInitialMessageDispatchStep
import com.intellij.platform.ai.agent.sessions.core.providers.AgentInitialMessageMode
import com.intellij.platform.ai.agent.sessions.core.providers.AgentInitialMessagePlan
import com.intellij.platform.ai.agent.sessions.core.providers.AgentInitialMessageStartupPolicy
import com.intellij.platform.ai.agent.sessions.core.providers.AgentPromptProviderOption
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionProviderDescriptor
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionSource
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionTerminalLaunchSpec
import com.intellij.platform.ai.agent.sessions.core.providers.AgentThreadRenameAction
import com.intellij.platform.ai.agent.sessions.core.providers.buildPlanModeInitialMessagePlan
import com.intellij.platform.ai.agent.sessions.core.providers.isPlanModeRequested
import com.intellij.openapi.project.Project
import javax.swing.Icon

internal class JunieAgentSessionProviderDescriptor(
  override val sessionSource: AgentSessionSource = JunieSessionSource(),
  private val threadMutationBackend: JunieSessionThreadMutationBackend =
    (sessionSource as? JunieSessionSource)?.sessionIndexStore ?: JunieSessionIndexStore(),
  private val executableResolver: suspend () -> String = JunieCliSupport::resolveExecutableOrDefaultViaTerminalResolver,
  private val cliInfoResolver: suspend () -> JunieCliInfo? = JunieCliSupport::resolveCliInfoViaTerminalResolver,
  private val generationModelCatalogResolver: suspend (String, String) -> List<AgentPromptGenerationModel> =
    JunieAcpGenerationModelCatalog::listAvailableGenerationModels,
) : AgentSessionProviderDescriptor {
  @Volatile
  private var latestCliInfo: JunieCliInfo? = null

  override val provider: AgentSessionProvider
    get() = AgentSessionProvider.JUNIE

  override val displayPriority: Int
    get() = 2

  override val displayNameKey: String
    get() = "toolwindow.provider.junie"

  override val newSessionLabelKey: String
    get() = "toolwindow.action.new.session.junie"

  override val yoloSessionLabelKey: String
    get() = "toolwindow.action.new.session.junie.yolo"

  override val yoloSessionModeLabelKey: String
    get() = "toolwindow.action.new.session.junie.yolo.mode"

  override val icon: Icon
    get() = AgentWorkbenchCommonIcons.Junie

  override val monochromeIcon: Icon
    get() = AgentWorkbenchCommonIcons.JunieGray

  override val supportedLaunchModes: Set<AgentSessionLaunchMode>
    get() = setOf(AgentSessionLaunchMode.STANDARD, AgentSessionLaunchMode.YOLO)

  override val promptOptions: List<AgentPromptProviderOption>
    get() = if (supportsInteractivePromptLaunch()) listOf(JUNIE_PROMPT_PROVIDER_PLAN_MODE_OPTION) else emptyList()

  override val supportedReasoningEfforts: Set<AgentPromptReasoningEffort>
    get() = setOf(
      AgentPromptReasoningEffort.LOW,
      AgentPromptReasoningEffort.MEDIUM,
      AgentPromptReasoningEffort.HIGH,
      AgentPromptReasoningEffort.XHIGH,
    )

  override val supportsGenerationModelSelection: Boolean
    get() = true

  override val supportsPendingEditorTabRebind: Boolean
    get() = true

  override val emitsScopedRefreshSignals: Boolean
    get() = true

  override val refreshPathAfterCreateNewSession: Boolean
    get() = true

  override val requiresCliAvailabilityForInitialMessagePlan: Boolean
    get() = true

  override val cliMissingMessageKey: String
    get() = "toolwindow.error.junie.cli"

  override val terminalAgentKey: String
    get() = JunieCliSupport.JUNIE_TERMINAL_AGENT_KEY

  override val archiveRefreshDelayMs: Long
    get() = 1_000L

  override val suppressArchivedThreadsDuringRefresh: Boolean
    get() = true

  override val supportsArchiveThread: Boolean
    get() = true

  override val supportsUnarchiveThread: Boolean
    get() = true

  override val pendingSessionLaunchYoloMarker: String
    get() = BRAVE_FLAG

  override val threadRenameAction: AgentThreadRenameAction = { path, threadId, normalizedName ->
    threadMutationBackend.renameThread(path, threadId, normalizedName)
  }

  override suspend fun isCliAvailable(): Boolean {
    val cliInfo = cliInfoResolver()
    latestCliInfo = cliInfo
    return cliInfo != null
  }

  override suspend fun buildResumeLaunchSpec(sessionId: String): AgentSessionTerminalLaunchSpec {
    return buildResumeLaunchSpec(sessionId, AgentSessionLaunchMode.STANDARD)
  }

  override suspend fun buildResumeLaunchSpec(
    sessionId: String,
    launchMode: AgentSessionLaunchMode,
  ): AgentSessionTerminalLaunchSpec {
    return AgentSessionTerminalLaunchSpec(
      command = JunieCliSupport.buildResumeCommand(
        sessionId = sessionId,
        yolo = launchMode == AgentSessionLaunchMode.YOLO,
        executable = executableResolver(),
      ),
    )
  }

  override suspend fun buildNewSessionLaunchSpec(mode: AgentSessionLaunchMode): AgentSessionTerminalLaunchSpec {
    return AgentSessionTerminalLaunchSpec(
      command = JunieCliSupport.buildNewSessionCommand(yolo = mode == AgentSessionLaunchMode.YOLO, executable = executableResolver()),
    )
  }

  override suspend fun listAvailableGenerationModels(project: Project?): List<AgentPromptGenerationModel> {
    val projectPath = project?.basePath?.takeIf { it.isNotBlank() }
                      ?: System.getProperty("user.home")?.takeIf { it.isNotBlank() }
                      ?: return emptyList()
    return generationModelCatalogResolver(executableResolver(), projectPath)
  }

  override fun applyGenerationSettings(
    baseLaunchSpec: AgentSessionTerminalLaunchSpec,
    generationSettings: AgentPromptGenerationSettings,
    initialMessagePlan: AgentInitialMessagePlan,
  ): AgentSessionTerminalLaunchSpec {
    val settings = sanitizeGenerationSettings(generationSettings)
    val generationArgs = buildJunieGenerationArgs(settings)
    if (generationArgs.isEmpty()) {
      return baseLaunchSpec
    }
    return baseLaunchSpec.copy(
      command = replaceJunieGenerationArgs(baseLaunchSpec.command, generationArgs),
    )
  }

  override fun buildInitialMessagePlan(request: AgentPromptInitialMessageRequest): AgentInitialMessagePlan {
    if (request.isPlanModeRequested() && !supportsInteractivePromptLaunch()) {
      return AgentInitialMessagePlan.EMPTY
    }
    return buildPlanModeInitialMessagePlan(
      request = request,
      startupPolicyWhenPlanModeEnabled = AgentInitialMessageStartupPolicy.TRY_STARTUP_COMMAND,
    )
  }

  override fun buildLaunchSpecWithInitialMessage(
    baseLaunchSpec: AgentSessionTerminalLaunchSpec,
    initialMessagePlan: AgentInitialMessagePlan,
  ): AgentSessionTerminalLaunchSpec? {
    val message = initialMessagePlan.message ?: return baseLaunchSpec
    if (!supportsInteractivePromptLaunch()) {
      return null
    }
    return baseLaunchSpec.copy(
      command = JunieCliSupport.buildLaunchCommandWithInitialMessage(
        baseCommand = baseLaunchSpec.command,
        message = message,
        plan = initialMessagePlan.mode == AgentInitialMessageMode.PLAN,
      )
    )
  }

  override fun buildPostStartDispatchSteps(initialMessagePlan: AgentInitialMessagePlan): List<AgentInitialMessageDispatchStep> {
    if (initialMessagePlan.mode == AgentInitialMessageMode.PLAN) {
      return emptyList()
    }
    return super.buildPostStartDispatchSteps(initialMessagePlan)
  }

  override suspend fun archiveThread(path: String, threadId: String): Boolean {
    return threadMutationBackend.archiveThread(path, threadId)
  }

  override suspend fun unarchiveThread(path: String, threadId: String): Boolean {
    return threadMutationBackend.unarchiveThread(path, threadId)
  }

  private fun supportsInteractivePromptLaunch(): Boolean {
    return latestCliInfo?.supportsInteractivePromptLaunch == true
  }
}

private val JUNIE_PROMPT_PROVIDER_PLAN_MODE_OPTION: AgentPromptProviderOption =
  AGENT_PROMPT_PROVIDER_PLAN_MODE_OPTION.copy(defaultSelected = false)

private fun buildJunieGenerationArgs(
  settings: AgentPromptGenerationSettings,
): List<String> {
  val args = mutableListOf<String>()
  settings.modelId?.let { modelId -> args.addAll(listOf(MODEL_FLAG, modelId)) }
  val effort = settings.reasoningEffort
  if (effort != AgentPromptReasoningEffort.AUTO) {
    args.addAll(listOf(EFFORT_FLAG, effort.junieCliValue()))
  }
  return args
}

private fun replaceJunieGenerationArgs(command: List<String>, args: List<String>): List<String> {
  val commandWithoutGenerationArgs = removeOptions(command, JUNIE_GENERATION_FLAGS, beforeTokens = setOf(PROMPT_FLAG))
  return insertArgumentsBefore(commandWithoutGenerationArgs, args, beforeTokens = setOf(PROMPT_FLAG))
}

private fun AgentPromptReasoningEffort.junieCliValue(): String {
  return name.lowercase()
}

private const val MODEL_FLAG: String = "--model"
private const val EFFORT_FLAG: String = "--effort"
private const val PROMPT_FLAG: String = "--prompt"
private val JUNIE_GENERATION_FLAGS: Set<String> = setOf(MODEL_FLAG, EFFORT_FLAG)
