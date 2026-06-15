// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.junie.sessions

import com.intellij.agent.workbench.common.icons.AgentWorkbenchCommonIcons
import com.intellij.agent.workbench.common.session.AgentSessionLaunchMode
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.junie.common.BRAVE_FLAG
import com.intellij.agent.workbench.junie.common.JunieCliInfo
import com.intellij.agent.workbench.junie.common.JunieCliSupport
import com.intellij.agent.workbench.prompt.core.AgentPromptGenerationSettings
import com.intellij.agent.workbench.prompt.core.AgentPromptGenerationModel
import com.intellij.agent.workbench.prompt.core.AgentPromptInitialMessageRequest
import com.intellij.agent.workbench.prompt.core.AgentPromptReasoningEffort
import com.intellij.agent.workbench.sessions.core.providers.AGENT_PROMPT_PROVIDER_PLAN_MODE_OPTION
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
    get() = listOf(JUNIE_PROMPT_PROVIDER_PLAN_MODE_OPTION)

  override val supportedReasoningEfforts: Set<AgentPromptReasoningEffort>
    get() = setOf(
      AgentPromptReasoningEffort.LOW,
      AgentPromptReasoningEffort.MEDIUM,
      AgentPromptReasoningEffort.HIGH,
      AgentPromptReasoningEffort.XHIGH,
    )

  override val supportsGenerationModelSelection: Boolean
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
    return buildPlanModeInitialMessagePlan(
      request = request,
      startupPolicyWhenPlanModeEnabled = if (supportsInteractivePromptLaunch()) {
        AgentInitialMessageStartupPolicy.TRY_STARTUP_COMMAND
      }
      else {
        AgentInitialMessageStartupPolicy.POST_START_ONLY
      },
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
    if (initialMessagePlan.mode != AgentInitialMessageMode.PLAN) {
      return super.buildPostStartDispatchSteps(initialMessagePlan)
    }

    return buildTerminalPlanModePostStartDispatchSteps(initialMessagePlan)
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
  val promptIndex = command.indexOf(PROMPT_FLAG).takeIf { it >= 0 } ?: command.size
  val result = command.subList(0, promptIndex).withoutJunieGenerationArgs().toMutableList()
  result.addAll(args)
  result.addAll(command.subList(promptIndex, command.size))
  return result
}

private fun List<String>.withoutJunieGenerationArgs(): List<String> {
  val result = mutableListOf<String>()
  var index = 0
  while (index < size) {
    val token = this[index]
    if (token == MODEL_FLAG || token == EFFORT_FLAG) {
      index += if (index + 1 < size) 2 else 1
    }
    else {
      result.add(token)
      index++
    }
  }
  return result
}

private fun AgentPromptReasoningEffort.junieCliValue(): String {
  return name.lowercase()
}

private const val MODEL_FLAG: String = "--model"
private const val EFFORT_FLAG: String = "--effort"
private const val PROMPT_FLAG: String = "--prompt"
