// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.core.launch

import com.intellij.agent.workbench.common.session.AgentSessionLaunchMode
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.prompt.core.AgentPromptGenerationModel
import com.intellij.agent.workbench.prompt.core.AgentPromptGenerationSettings
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessagePlan
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderDescriptor
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviders
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionTerminalLaunchSpec
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import kotlin.coroutines.cancellation.CancellationException

private val LOG = logger<AgentSessionLaunchPlanner>()

enum class AgentSessionLaunchOperation {
  NEW,
  RESUME,
}

data class AgentSessionLaunchIntent(
  @JvmField val projectPath: String,
  val provider: AgentSessionProvider,
  @JvmField val operation: AgentSessionLaunchOperation,
  @JvmField val sessionId: String? = null,
  @JvmField val launchMode: AgentSessionLaunchMode = AgentSessionLaunchMode.STANDARD,
  @JvmField val generationSettings: AgentPromptGenerationSettings = AgentPromptGenerationSettings.AUTO,
)

data class AgentSessionPlannedLaunch(
  @JvmField val intent: AgentSessionLaunchIntent,
  @JvmField val baseLaunchSpec: AgentSessionTerminalLaunchSpec,
  @JvmField val launchSpec: AgentSessionTerminalLaunchSpec,
  @JvmField val generationModelCatalog: List<AgentPromptGenerationModel>,
)

object AgentSessionLaunchPlanner {
  suspend fun plan(
    intent: AgentSessionLaunchIntent,
    project: Project? = null,
    initialMessagePlan: AgentInitialMessagePlan = AgentInitialMessagePlan.EMPTY,
    generationModelCatalog: List<AgentPromptGenerationModel> = emptyList(),
    extraEnvVariables: Map<String, String> = emptyMap(),
    extraCommandArgs: List<String> = emptyList(),
    resumeLaunchSpecProvider: (suspend (AgentSessionProvider, String, AgentSessionLaunchMode) -> AgentSessionTerminalLaunchSpec)? = null,
  ): AgentSessionPlannedLaunch {
    val descriptor = AgentSessionProviders.find(intent.provider)
    val baseLaunchSpec = buildBaseLaunchSpec(intent, descriptor, resumeLaunchSpecProvider)
    val sanitizedIntent = intent.copy(
      generationSettings = descriptor?.sanitizeGenerationSettings(intent.generationSettings) ?: AgentPromptGenerationSettings.AUTO,
    )
    val generationLaunchSpec = descriptor?.applyGenerationSettings(
      baseLaunchSpec = baseLaunchSpec,
      generationSettings = sanitizedIntent.generationSettings,
      initialMessagePlan = initialMessagePlan,
    ) ?: baseLaunchSpec
    val resolvedCatalog = resolveGenerationModelCatalog(
      descriptor = descriptor,
      project = project,
      generationSettings = sanitizedIntent.generationSettings,
      generationModelCatalog = generationModelCatalog,
    )
    val catalogLaunchSpec = descriptor?.applyGenerationModelCatalog(
      baseLaunchSpec = generationLaunchSpec,
      generationSettings = sanitizedIntent.generationSettings,
      generationModelCatalog = resolvedCatalog,
    ) ?: generationLaunchSpec
    val augmentedLaunchSpec = AgentSessionLaunchSpecs.augment(
      projectPath = sanitizedIntent.projectPath,
      provider = sanitizedIntent.provider,
      launchSpec = catalogLaunchSpec,
    )
    val contributedLaunchSpec = AgentSessionLaunchContributors.applyAll(
      projectPath = sanitizedIntent.projectPath,
      provider = sanitizedIntent.provider,
      sessionId = sanitizedIntent.sessionId.takeIf { sanitizedIntent.operation == AgentSessionLaunchOperation.RESUME },
      launchSpec = augmentedLaunchSpec,
    )
    return AgentSessionPlannedLaunch(
      intent = sanitizedIntent,
      baseLaunchSpec = baseLaunchSpec,
      launchSpec = applyTransientExtras(
        launchSpec = contributedLaunchSpec,
        extraEnvVariables = extraEnvVariables,
        extraCommandArgs = extraCommandArgs,
      ),
      generationModelCatalog = resolvedCatalog,
    )
  }

  private suspend fun buildBaseLaunchSpec(
    intent: AgentSessionLaunchIntent,
    descriptor: AgentSessionProviderDescriptor?,
    resumeLaunchSpecProvider: (suspend (AgentSessionProvider, String, AgentSessionLaunchMode) -> AgentSessionTerminalLaunchSpec)?,
  ): AgentSessionTerminalLaunchSpec {
    return when (intent.operation) {
      AgentSessionLaunchOperation.NEW -> {
        requireNotNull(descriptor) { "Missing Agent Workbench provider descriptor for ${intent.provider.value}" }
        descriptor.buildNewSessionLaunchSpec(intent.launchMode)
      }
      AgentSessionLaunchOperation.RESUME -> {
        val sessionId = requireNotNull(intent.sessionId) { "Resume launch intent must contain a session id" }
        resumeLaunchSpecProvider?.let { provider ->
          return provider(intent.provider, sessionId, intent.launchMode)
        }
        descriptor?.buildResumeLaunchSpec(sessionId, intent.launchMode)
        ?: AgentSessionTerminalLaunchSpec(command = listOf(intent.provider.value, "resume", sessionId))
      }
    }
  }

  private suspend fun resolveGenerationModelCatalog(
    descriptor: AgentSessionProviderDescriptor?,
    project: Project?,
    generationSettings: AgentPromptGenerationSettings,
    generationModelCatalog: List<AgentPromptGenerationModel>,
  ): List<AgentPromptGenerationModel> {
    if (descriptor?.supportsGenerationModelSelection != true) {
      return emptyList()
    }
    if (generationModelCatalog.isNotEmpty()) {
      return generationModelCatalog
    }
    if (generationSettings == AgentPromptGenerationSettings.AUTO && !descriptor.resolvesGenerationModelCatalogForAutoSettings) {
      return emptyList()
    }
    return try {
      descriptor.listAvailableGenerationModels(project)
    }
    catch (e: CancellationException) {
      throw e
    }
    catch (e: Throwable) {
      LOG.warn("Failed to refresh generation model catalog for provider '${descriptor.provider.value}' during launch planning", e)
      emptyList()
    }
  }
}

private fun applyTransientExtras(
  launchSpec: AgentSessionTerminalLaunchSpec,
  extraEnvVariables: Map<String, String>,
  extraCommandArgs: List<String>,
): AgentSessionTerminalLaunchSpec {
  if (extraEnvVariables.isEmpty() && extraCommandArgs.isEmpty()) {
    return launchSpec
  }
  return launchSpec.copy(
    command = if (extraCommandArgs.isEmpty()) launchSpec.command else launchSpec.command + extraCommandArgs,
    envVariables = if (extraEnvVariables.isEmpty()) launchSpec.envVariables else launchSpec.envVariables + extraEnvVariables,
  )
}
