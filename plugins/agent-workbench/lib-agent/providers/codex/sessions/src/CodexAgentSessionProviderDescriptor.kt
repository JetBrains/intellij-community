// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ai.agent.codex.sessions

import com.intellij.platform.ai.agent.codex.common.CodexCliNotFoundException
import com.intellij.platform.ai.agent.codex.common.CodexCliUtils
import com.intellij.platform.ai.agent.codex.sessions.backend.appserver.SharedCodexAppServerService
import com.intellij.platform.ai.agent.common.icons.AgentWorkbenchCommonIcons
import com.intellij.platform.ai.agent.core.session.AgentSessionLaunchMode
import com.intellij.agent.workbench.prompt.core.AgentPromptGenerationModel
import com.intellij.agent.workbench.prompt.core.AgentPromptGenerationModelGroup
import com.intellij.agent.workbench.prompt.core.AgentPromptGenerationSettings
import com.intellij.agent.workbench.prompt.core.AgentPromptInitialMessageRequest
import com.intellij.agent.workbench.prompt.core.AgentPromptReasoningEffort
import com.intellij.agent.workbench.prompt.core.AgentPromptReusableSourceEntry
import com.intellij.agent.workbench.prompt.core.AgentPromptReusableSourceKind
import com.intellij.agent.workbench.prompt.core.withGroup
import com.intellij.platform.ai.agent.sessions.core.launch.insertArgumentsBefore
import com.intellij.platform.ai.agent.sessions.core.providers.AGENT_PROMPT_PROVIDER_PLAN_MODE_OPTION
import com.intellij.platform.ai.agent.sessions.core.providers.AgentInitialMessageDispatchAction
import com.intellij.platform.ai.agent.sessions.core.providers.AgentInitialMessageDispatchStep
import com.intellij.platform.ai.agent.sessions.core.providers.AgentInitialMessageMode
import com.intellij.platform.ai.agent.sessions.core.providers.AgentInitialMessageProviderDispatchRequest
import com.intellij.platform.ai.agent.sessions.core.providers.AgentInitialMessagePlan
import com.intellij.platform.ai.agent.sessions.core.providers.AgentInitialMessageStartupPolicy
import com.intellij.platform.ai.agent.sessions.core.providers.AgentInitialPromptDeliveryChannel
import com.intellij.platform.ai.agent.sessions.core.providers.AgentInitialPromptDeliveryPlan
import com.intellij.platform.ai.agent.sessions.core.providers.AgentInitialPromptDeliveryStatus
import com.intellij.platform.ai.agent.sessions.core.providers.AgentInitialPromptRecord
import com.intellij.platform.ai.agent.sessions.core.providers.AgentTerminalPromptDispatch
import com.intellij.platform.ai.agent.sessions.core.providers.AgentPrestartedNewSessionLaunch
import com.intellij.platform.ai.agent.sessions.core.providers.AgentPromptProviderOption
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionProviderImplementation
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionSource
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionTerminalLaunchSpec
import com.intellij.platform.ai.agent.sessions.core.providers.AgentThreadRenameAction
import com.intellij.platform.ai.agent.sessions.core.providers.buildPlanModeInitialMessagePlan
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import java.nio.file.Path
import java.util.concurrent.CancellationException
import javax.swing.Icon

private val CODEX_SESSION_PROVIDER_LOG = logger<CodexAgentSessionProviderDescriptor>()

internal class CodexAgentSessionProviderDescriptor(
  override val sessionSource: AgentSessionSource = CodexSessionSource(),
  private val threadMutationBackend: CodexThreadMutationBackend = SharedServiceCodexThreadMutationBackend,
  private val threadStartupBackend: CodexThreadStartupBackend = SharedServiceCodexThreadStartupBackend,
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
  private val themeLaunchConfigResolver: () -> CodexThemeLaunchConfig? = CodexThemeSupport.DEFAULT::launchConfigOrNull,
) : AgentSessionProviderImplementation {
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
    val themeLaunchConfig = resolveThemeLaunchConfigOrNull()
    val remoteUrl = threadStartupBackend.currentRemoteUrl()
    val command = if (launchMode == AgentSessionLaunchMode.YOLO) {
      buildCodexBaseCommand(executable, themeLaunchConfig) + listOf("--yolo", "resume", "--remote", remoteUrl, sessionId)
    }
    else {
      buildCodexBaseCommand(executable, themeLaunchConfig) + listOf("resume", "--remote", remoteUrl, sessionId)
    }
    return AgentSessionTerminalLaunchSpec(
      command = command,
    )
  }

  override suspend fun buildNewSessionLaunchSpec(mode: AgentSessionLaunchMode): AgentSessionTerminalLaunchSpec {
    val executable = executableResolver()
    val themeLaunchConfig = resolveThemeLaunchConfigOrNull()
    val command = if (mode == AgentSessionLaunchMode.YOLO) {
      buildCodexBaseCommand(executable, themeLaunchConfig) + "--yolo"
    }
    else {
      buildCodexBaseCommand(executable, themeLaunchConfig)
    }
    return AgentSessionTerminalLaunchSpec(command = command)
  }

  override suspend fun prestartNewSessionLaunch(
    projectPath: String,
    launchMode: AgentSessionLaunchMode,
    initialMessagePlan: AgentInitialMessagePlan,
    generationSettings: AgentPromptGenerationSettings,
    generationModelCatalog: List<AgentPromptGenerationModel>,
    launchSpec: AgentSessionTerminalLaunchSpec,
  ): AgentPrestartedNewSessionLaunch {
    val prestarted = threadStartupBackend.prestartThread(
      projectPath = projectPath,
      launchMode = launchMode,
      model = generationSettings.modelId,
    )
    return AgentPrestartedNewSessionLaunch(
      launchSpec = launchSpec.copy(
        command = launchSpec.command + listOf("resume", "--remote", prestarted.remoteUrl, prestarted.threadId),
        preallocatedSessionId = prestarted.threadId,
      ),
      initialMessageDispatchPlan = buildAppServerInitialMessageDispatchPlan(initialMessagePlan),
    )
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

  override suspend fun dispatchInitialMessageToProvider(request: AgentInitialMessageProviderDispatchRequest): Boolean {
    if (request.message.isBlank()) {
      return false
    }
    threadStartupBackend.startTurn(
      threadId = request.threadId,
      prompt = request.message,
      mode = request.mode,
      model = request.generationSettings.modelId,
      reasoningEffort = resolvePlanReasoningEffort(request.generationSettings),
    )
    return true
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
          provider = CODEX_AGENT_SESSION_PROVIDER,
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
    val message = initialMessagePlan.message?.takeIf { it.isNotBlank() } ?: return emptyList()
    return listOf(
      AgentInitialMessageDispatchStep(
        text = message,
        timeoutPolicy = initialMessagePlan.timeoutPolicy,
        action = AgentInitialMessageDispatchAction.PROVIDER,
      )
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

  private fun resolveThemeLaunchConfigOrNull(): CodexThemeLaunchConfig? {
    return try {
      themeLaunchConfigResolver()
    }
    catch (e: CancellationException) {
      throw e
    }
    catch (e: Exception) {
      CODEX_SESSION_PROVIDER_LOG.warn("Failed to resolve Codex theme launch config", e)
      null
    }
  }
}

internal interface CodexThreadMutationBackend {
  suspend fun archiveThread(path: String, threadId: String)

  suspend fun unarchiveThread(path: String, threadId: String)

  suspend fun setThreadName(path: String, threadId: String, name: String)
}

internal interface CodexThreadStartupBackend {
  suspend fun currentRemoteUrl(): String

  suspend fun prestartThread(
    projectPath: String,
    launchMode: AgentSessionLaunchMode,
    model: String?,
  ): CodexPrestartedThread

  suspend fun startTurn(
    threadId: String,
    prompt: String,
    mode: AgentInitialMessageMode,
    model: String?,
    reasoningEffort: String?,
  )
}

internal data class CodexPrestartedThread(
  @JvmField val threadId: String,
  @JvmField val remoteUrl: String,
)

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

private object SharedServiceCodexThreadStartupBackend : CodexThreadStartupBackend {
  override suspend fun currentRemoteUrl(): String {
    return serviceAsync<SharedCodexAppServerService>().currentRemoteUrl()
  }

  override suspend fun prestartThread(
    projectPath: String,
    launchMode: AgentSessionLaunchMode,
    model: String?,
  ): CodexPrestartedThread {
    val prestarted = serviceAsync<SharedCodexAppServerService>().prestartThread(
      cwd = projectPath,
      yolo = launchMode == AgentSessionLaunchMode.YOLO,
      model = model,
    )
    return CodexPrestartedThread(
      threadId = prestarted.threadId,
      remoteUrl = prestarted.remoteUrl,
    )
  }

  override suspend fun startTurn(
    threadId: String,
    prompt: String,
    mode: AgentInitialMessageMode,
    model: String?,
    reasoningEffort: String?,
  ) {
    serviceAsync<SharedCodexAppServerService>().startInitialPromptTurn(
      threadId = threadId,
      prompt = prompt,
      mode = mode,
      model = model,
      reasoningEffort = reasoningEffort,
    )
  }
}

private fun buildAppServerInitialMessageDispatchPlan(initialMessagePlan: AgentInitialMessagePlan): AgentInitialPromptDeliveryPlan? {
  val prompt = initialMessagePlan.message?.trim()?.takeIf { it.isNotEmpty() } ?: return null
  return AgentInitialPromptDeliveryPlan(
    promptRecord = AgentInitialPromptRecord(
      message = prompt,
      mode = initialMessagePlan.mode,
      deliveryStatus = AgentInitialPromptDeliveryStatus.PENDING,
      deliveryChannel = AgentInitialPromptDeliveryChannel.APP_SERVER,
    ),
    terminalDispatch = AgentTerminalPromptDispatch(
      steps = listOf(
        AgentInitialMessageDispatchStep(
          text = prompt,
          timeoutPolicy = initialMessagePlan.timeoutPolicy,
          action = AgentInitialMessageDispatchAction.PROVIDER,
        )
      )
    ).normalized(),
  )
}

private fun buildCodexBaseCommand(executable: String, themeLaunchConfig: CodexThemeLaunchConfig?): List<String> {
  val command = mutableListOf(
    executable,
    "-c",
    CODEX_AUTO_UPDATE_CONFIG,
    "-c",
    CODEX_TERMINAL_TITLE_CONFIG,
  )
  themeLaunchConfig?.let { config ->
    command.add("-c")
    command.add(config.themeConfigValue)
  }
  return command
}

private fun resolvePlanReasoningEffort(settings: AgentPromptGenerationSettings): String? {
  val effort = settings.planReasoningEffort ?: settings.reasoningEffort
  return effort.takeIf { it != AgentPromptReasoningEffort.AUTO }?.codexConfigValue()
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
  return insertArgumentsBefore(command, args, beforeTokens = setOf("--"))
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
