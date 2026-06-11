// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.pi.sessions

// @spec community/plugins/agent-workbench/spec/agent-sessions-pi.spec.md

import com.intellij.agent.workbench.common.icons.AgentWorkbenchCommonIcons
import com.intellij.agent.workbench.common.session.AgentSessionLaunchMode
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.prompt.core.AgentPromptGenerationSettings
import com.intellij.agent.workbench.prompt.core.AgentPromptGenerationModel
import com.intellij.agent.workbench.prompt.core.AgentPromptInitialMessageRequest
import com.intellij.agent.workbench.prompt.core.AgentPromptReasoningEffort
import com.intellij.agent.workbench.sessions.AgentSessionsBundle
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessagePlan
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderCliVisibilityPolicy
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderDescriptor
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSource
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionTerminalLaunchSpec
import com.intellij.agent.workbench.sessions.core.providers.AgentThreadRenameAction
import com.intellij.agent.workbench.sessions.core.settings.AgentWorkbenchCheckboxSetting
import com.intellij.agent.workbench.sessions.settings.AgentSessionProviderSettingsService
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import java.util.UUID
import javax.swing.Icon

private val LOG = logger<PiAgentSessionProviderDescriptor>()

internal val PI_SUPPORTED_REASONING_EFFORTS: Set<AgentPromptReasoningEffort> = setOf(
  AgentPromptReasoningEffort.LOW,
  AgentPromptReasoningEffort.MEDIUM,
  AgentPromptReasoningEffort.HIGH,
  AgentPromptReasoningEffort.XHIGH,
)

internal class PiAgentSessionProviderDescriptor(
  override val sessionSource: AgentSessionSource = PiSessionSource(),
  private val threadMutationBackend: PiSessionThreadMutationBackend =
    (sessionSource as? PiSessionSource)?.sessionStore ?: PiSessionStore(),
  private val executableResolver: suspend () -> String = PiCliSupport::resolveExecutableOrDefaultViaTerminalResolver,
  private val cliAvailableProbe: suspend () -> Boolean = { PiCliSupport.findExecutableViaTerminalResolver() != null },
  private val sessionIdGenerator: () -> String = { UUID.randomUUID().toString() },
  private val extensionLaunchResourcesResolver: () -> PiExtensionLaunchResources? = PiThemeSupport.DEFAULT::launchResourcesOrNull,
  private val statusLaunchEnvironmentResolver: (String) -> Map<String, String> = PiExtensionStatusBridge::createLaunchEnvironment,
  private val omlxGenerationModelCatalogResolver: suspend () -> List<AgentPromptGenerationModel> =
    PiOmlxModelCatalog.DEFAULT::listAvailableGenerationModels,
  private val jbCentralGenerationModelCatalogResolver: suspend (String, String?) -> List<AgentPromptGenerationModel> =
    PiJbCentralModelCatalog.DEFAULT::listAvailableGenerationModels,
  private val omlxSupportEnabledResolver: () -> Boolean = PiOmlxSupportSettings::isEnabled,
  private val jbCentralSupportEnabledResolver: () -> Boolean = PiJbCentralSupportSettings::isEnabled,
) : AgentSessionProviderDescriptor {
  override val provider: AgentSessionProvider
    get() = AgentSessionProvider.PI

  override val displayPriority: Int
    get() = 3

  override val displayNameKey: String
    get() = "toolwindow.provider.pi"

  override val newSessionLabelKey: String
    get() = "toolwindow.action.new.session.pi"

  override val icon: Icon
    get() = AgentWorkbenchCommonIcons.PI

  override val cliMissingMessageKey: String
    get() = "toolwindow.error.pi.cli"

  override val cliVisibilityPolicy: AgentSessionProviderCliVisibilityPolicy
    get() = AgentSessionProviderCliVisibilityPolicy.DISCOVER_WHEN_AVAILABLE

  override val supportsNewThreadRebind: Boolean
    get() = true

  override val emitsScopedRefreshSignals: Boolean
    get() = true

  override val refreshPathAfterCreateNewSession: Boolean
    get() = true

  override val terminalAgentKey: String
    get() = PiCliSupport.PI_TERMINAL_AGENT_KEY

  override val supportsGenerationModelSelection: Boolean
    get() = omlxSupportEnabledResolver() || jbCentralSupportEnabledResolver()

  override val providerSettings: List<AgentWorkbenchCheckboxSetting>
    get() = listOf(
      AgentWorkbenchCheckboxSetting(
        text = AgentSessionsBundle.message("settings.agent.workbench.provider.pi.omlx.models"),
        description = AgentSessionsBundle.message("settings.agent.workbench.provider.pi.omlx.models.description"),
        isSelected = PiOmlxSupportSettings::isEnabled,
        setSelected = PiOmlxSupportSettings::setEnabled,
      ),
      AgentWorkbenchCheckboxSetting(
        text = AgentSessionsBundle.message("settings.agent.workbench.provider.pi.jbcentral.models"),
        description = AgentSessionsBundle.message("settings.agent.workbench.provider.pi.jbcentral.models.description"),
        isSelected = PiJbCentralSupportSettings::isEnabled,
        setSelected = PiJbCentralSupportSettings::setEnabled,
      )
    )

  override val archiveRefreshDelayMs: Long
    get() = 1_000L

  override val suppressArchivedThreadsDuringRefresh: Boolean
    get() = true

  override val supportsArchiveThread: Boolean
    get() = true

  override val supportsUnarchiveThread: Boolean
    get() = true

  override val threadRenameAction: AgentThreadRenameAction = { path, threadId, normalizedName ->
    threadMutationBackend.renameThread(path, threadId, normalizedName)
  }

  override suspend fun isCliAvailable(): Boolean = cliAvailableProbe()

  override suspend fun listAvailableGenerationModels(project: Project?): List<AgentPromptGenerationModel> {
    if (!supportsGenerationModelSelection) {
      return emptyList()
    }
    return buildList {
      if (omlxSupportEnabledResolver()) {
        addAll(omlxGenerationModelCatalogResolver())
      }
      if (jbCentralSupportEnabledResolver()) {
        val piExecutable = executableResolver()
        val extensionPath = resolveExtensionLaunchResources()?.extensionPath?.toString()
        addAll(jbCentralGenerationModelCatalogResolver(piExecutable, extensionPath))
      }
    }
  }

  override suspend fun buildResumeLaunchSpec(sessionId: String): AgentSessionTerminalLaunchSpec {
    return buildPiLaunchSpec(sessionFlag = PI_SESSION_FLAG, sessionId = sessionId)
  }

  override suspend fun buildNewSessionLaunchSpec(mode: AgentSessionLaunchMode): AgentSessionTerminalLaunchSpec {
    val sessionId = sessionIdGenerator()
    return buildPiLaunchSpec(sessionFlag = PI_SESSION_ID_FLAG, sessionId = sessionId).copy(preallocatedSessionId = sessionId)
  }

  private suspend fun buildPiLaunchSpec(sessionFlag: String, sessionId: String): AgentSessionTerminalLaunchSpec {
    val command = ArrayList<String>(4)
    val envVariables = LinkedHashMap<String, String>()
    command.add(executableResolver())
    resolveExtensionLaunchResources()?.let { resources ->
      command.add(PI_EXTENSION_FLAG)
      command.add(resources.extensionPath.toString())
      envVariables[PI_THEME_STATE_ENVIRONMENT_VARIABLE] = resources.stateFilePath.toString()
      envVariables.putAll(statusLaunchEnvironmentResolver(sessionId))
    }
    command.add(sessionFlag)
    command.add(sessionId)
    return AgentSessionTerminalLaunchSpec(command = command, envVariables = envVariables)
  }

  private fun resolveExtensionLaunchResources(): PiExtensionLaunchResources? {
    return try {
      extensionLaunchResourcesResolver()
    }
    catch (e: Exception) {
      LOG.warn("Failed to resolve Pi extension", e)
      null
    }
  }

  override fun sanitizeGenerationSettings(generationSettings: AgentPromptGenerationSettings): AgentPromptGenerationSettings {
    if (!supportsGenerationModelSelection) {
      return AgentPromptGenerationSettings.AUTO
    }
    val modelId = generationSettings.modelId
      ?.trim()
      ?.takeIf { it.isNotEmpty() }
      ?.takeIf(::isEnabledPiGenerationModelId)
    val reasoningEffort = generationSettings.reasoningEffort
                            .takeIf { effort ->
                              effort == AgentPromptReasoningEffort.AUTO ||
                              (modelId != null && supportsPiReasoningEffort(modelId, effort))
                            }
                          ?: AgentPromptReasoningEffort.AUTO
    return AgentPromptGenerationSettings(modelId = modelId, reasoningEffort = reasoningEffort)
  }

  private fun isEnabledPiGenerationModelId(modelId: String): Boolean {
    return (omlxSupportEnabledResolver() && PiOmlxModelCatalog.decodeGenerationModelId(modelId) != null) ||
           (jbCentralSupportEnabledResolver() && PiJbCentralModelCatalog.decodeGenerationModelId(modelId) != null)
  }

  private fun supportsPiReasoningEffort(modelId: String, effort: AgentPromptReasoningEffort): Boolean {
    if (effort !in PI_SUPPORTED_REASONING_EFFORTS) {
      return false
    }
    if (omlxSupportEnabledResolver() && PiOmlxModelCatalog.decodeGenerationModelId(modelId)?.reasoning == true) {
      return true
    }
    return jbCentralSupportEnabledResolver() && PiJbCentralModelCatalog.decodeGenerationModelId(modelId) != null
  }

  override fun applyGenerationSettings(
    baseLaunchSpec: AgentSessionTerminalLaunchSpec,
    generationSettings: AgentPromptGenerationSettings,
  ): AgentSessionTerminalLaunchSpec {
    val settings = sanitizeGenerationSettings(generationSettings)
    val sanitizedModelId = settings.modelId ?: return baseLaunchSpec
    val reasoningArgs = buildPiReasoningArgs(settings.reasoningEffort)
    PiOmlxModelCatalog.decodeGenerationModelId(sanitizedModelId)?.let { modelSelection ->
      return baseLaunchSpec.copy(
        command = insertPiGenerationArgs(
          command = baseLaunchSpec.command,
          args = listOf(PI_PROVIDER_FLAG, modelSelection.baseUrl, PI_MODEL_FLAG, modelSelection.modelId) + reasoningArgs,
        ),
        envVariables = baseLaunchSpec.envVariables + mapOf(
          PI_OMLX_PROVIDER_ENVIRONMENT_VARIABLE to PiOmlxModelCatalog.toLaunchEnvironmentValue(modelSelection)
        ),
      )
    }
    val jbCentralModelSelection = PiJbCentralModelCatalog.decodeGenerationModelId(sanitizedModelId) ?: return baseLaunchSpec
    return baseLaunchSpec.copy(
      command = insertPiGenerationArgs(
        command = baseLaunchSpec.command,
        args = listOf(PI_PROVIDER_FLAG, jbCentralModelSelection.provider, PI_MODEL_FLAG, jbCentralModelSelection.modelId) + reasoningArgs,
      ),
      envVariables = baseLaunchSpec.envVariables + mapOf(
        PI_JBCENTRAL_PROVIDER_ENVIRONMENT_VARIABLE to PiJbCentralModelCatalog.toLaunchEnvironmentValue(jbCentralModelSelection)
      ),
    )
  }

  override fun buildLaunchSpecWithInitialMessage(
    baseLaunchSpec: AgentSessionTerminalLaunchSpec,
    initialMessagePlan: AgentInitialMessagePlan,
  ): AgentSessionTerminalLaunchSpec {
    val message = initialMessagePlan.message ?: return baseLaunchSpec
    return baseLaunchSpec.copy(command = baseLaunchSpec.command + message)
  }

  override fun buildInitialMessagePlan(request: AgentPromptInitialMessageRequest): AgentInitialMessagePlan {
    return AgentInitialMessagePlan.composeDefault(request)
  }

  override suspend fun archiveThread(path: String, threadId: String): Boolean {
    return threadMutationBackend.archiveThread(path, threadId)
  }

  override suspend fun unarchiveThread(path: String, threadId: String): Boolean {
    return threadMutationBackend.unarchiveThread(path, threadId)
  }

  override fun recordTerminalSessionClosed(path: String, threadId: String) {
    PiExtensionStatusBridge.invalidateSession(threadId)
  }
}

private fun insertPiGenerationArgs(command: List<String>, args: List<String>): List<String> {
  val sessionFlagIndex = command.indexOfFirst { token -> token == PI_SESSION_FLAG || token == PI_SESSION_ID_FLAG }
                           .takeIf { index -> index >= 0 }
                         ?: command.size
  val result = command.subList(0, sessionFlagIndex).withoutPiGenerationArgs().toMutableList()
  result.addAll(args)
  result.addAll(command.subList(sessionFlagIndex, command.size))
  return result
}

private fun List<String>.withoutPiGenerationArgs(): List<String> {
  val result = mutableListOf<String>()
  var index = 0
  while (index < size) {
    val token = this[index]
    if (token == PI_PROVIDER_FLAG || token == PI_MODEL_FLAG || token == PI_THINKING_FLAG) {
      index += if (index + 1 < size) 2 else 1
    }
    else {
      result.add(token)
      index++
    }
  }
  return result
}

private fun buildPiReasoningArgs(reasoningEffort: AgentPromptReasoningEffort): List<String> {
  if (reasoningEffort == AgentPromptReasoningEffort.AUTO) {
    return emptyList()
  }
  return listOf(PI_THINKING_FLAG, reasoningEffort.piThinkingValue())
}

private fun AgentPromptReasoningEffort.piThinkingValue(): String {
  return name.lowercase()
}

private const val PI_EXTENSION_FLAG: String = "--extension"
private const val PI_PROVIDER_FLAG: String = "--provider"
private const val PI_MODEL_FLAG: String = "--model"
private const val PI_THINKING_FLAG: String = "--thinking"
private const val PI_SESSION_FLAG: String = "--session"
private const val PI_SESSION_ID_FLAG: String = "--session-id"

internal object PiOmlxSupportSettings {
  fun isEnabled(): Boolean {
    return service<AgentSessionProviderSettingsService>().isProviderFeatureEnabled(
      AgentSessionProvider.PI,
      PI_OMLX_PROVIDER_FEATURE_ID,
    )
  }

  fun setEnabled(enabled: Boolean) {
    service<AgentSessionProviderSettingsService>().setProviderFeatureEnabled(
      AgentSessionProvider.PI,
      PI_OMLX_PROVIDER_FEATURE_ID,
      enabled,
    )
  }
}

internal object PiJbCentralSupportSettings {
  fun isEnabled(): Boolean {
    return service<AgentSessionProviderSettingsService>().isProviderFeatureEnabled(
      AgentSessionProvider.PI,
      PI_JBCENTRAL_PROVIDER_FEATURE_ID,
    )
  }

  fun setEnabled(enabled: Boolean) {
    service<AgentSessionProviderSettingsService>().setProviderFeatureEnabled(
      AgentSessionProvider.PI,
      PI_JBCENTRAL_PROVIDER_FEATURE_ID,
      enabled,
    )
  }
}

private const val PI_OMLX_PROVIDER_FEATURE_ID: String = "omlx.models"
private const val PI_JBCENTRAL_PROVIDER_FEATURE_ID: String = "jbcentral.models"
