// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.launch.config.backend

// @spec community/plugins/agent-workbench/spec/agent-project-launch-config.spec.md

import com.intellij.agent.workbench.common.parseAgentWorkbenchPathOrNull
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.sessions.core.launch.AgentSessionLaunchSpecAugmenter
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionTerminalLaunchSpec
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.EelOsFamily
import com.intellij.platform.eel.provider.asEelPath
import com.intellij.platform.eel.provider.asNioPath
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.eel.provider.toEelApi
import com.intellij.platform.eel.provider.utils.toOs
import java.nio.file.Path

internal val AGENT_WORKBENCH_PROJECT_LAUNCH_CONFIG_LOG = logger<AgentWorkbenchProjectLaunchConfigLogCategory>()

private object AgentWorkbenchProjectLaunchConfigLogCategory

internal class AgentWorkbenchProjectLaunchConfigAugmenter(
  private val executionContextResolver: AgentWorkbenchLaunchExecutionContextResolver = DefaultAgentWorkbenchLaunchExecutionContextResolver,
  private val projectConfigCache: AgentWorkbenchProjectLaunchConfigCache = AgentWorkbenchProjectLaunchConfigCache.shared,
) : AgentSessionLaunchSpecAugmenter {
  override suspend fun augment(
    projectPath: String,
    provider: AgentSessionProvider,
    launchSpec: AgentSessionTerminalLaunchSpec,
  ): AgentSessionTerminalLaunchSpec {
    return try {
      val context = executionContextResolver.resolve(projectPath)
      if (context == null) {
        AGENT_WORKBENCH_PROJECT_LAUNCH_CONFIG_LOG.debug {
          "Skipped Agent Workbench launch augmentation for provider=${provider.value}: execution context unavailable for projectPath=$projectPath"
        }
        return launchSpec
      }
      val config = projectConfigCache.getProviderConfig(projectRoot = context.projectRoot, provider = provider)
      if (config == null) {
        AGENT_WORKBENCH_PROJECT_LAUNCH_CONFIG_LOG.debug {
          "No Agent Workbench launch augmentation resolved for provider=${provider.value}, projectRoot=${context.projectRoot}"
        }
        return launchSpec
      }
      val envVariables = buildAugmentedLaunchEnvironment(
        baseEnvVariables = launchSpec.envVariables,
        targetEnvironmentVariables = context.environmentVariables,
        systemDir = context.systemDir,
        osFamily = context.osFamily,
        provider = provider,
        config = config,
        targetPathStringResolver = context::toTargetPathString,
      )
      val envChanged = envVariables != launchSpec.envVariables
      AGENT_WORKBENCH_PROJECT_LAUNCH_CONFIG_LOG.debug {
        "Resolved Agent Workbench launch augmentation for provider=${provider.value}, projectRoot=${context.projectRoot}, envChanged=$envChanged, ${config.toDebugSummary()}"
      }
      if (!envChanged) launchSpec else launchSpec.copy(envVariables = envVariables)
    }
    catch (t: Throwable) {
      AGENT_WORKBENCH_PROJECT_LAUNCH_CONFIG_LOG.warn("Failed to resolve Agent Workbench launch config for $provider:$projectPath", t)
      launchSpec
    }
  }
}

internal fun interface AgentWorkbenchLaunchExecutionContextResolver {
  suspend fun resolve(projectPath: String): AgentWorkbenchLaunchExecutionContext?
}

internal data class AgentWorkbenchLaunchExecutionContext(
  val projectRoot: Path,
  val systemDir: Path,
  val osFamily: EelOsFamily,
  val environmentVariables: Map<String, String>,
  val targetPathStringResolver: (Path) -> String,
)

internal fun AgentWorkbenchLaunchExecutionContext.toTargetPathString(path: Path): String {
  return targetPathStringResolver(path)
}

private object DefaultAgentWorkbenchLaunchExecutionContextResolver : AgentWorkbenchLaunchExecutionContextResolver {
  override suspend fun resolve(projectPath: String): AgentWorkbenchLaunchExecutionContext? {
    val projectRoot = parseAgentWorkbenchPathOrNull(projectPath) ?: return null
    val eelDescriptor = projectRoot.getEelDescriptor()
    val eel = eelDescriptor.toEelApi()
    val environmentVariables = eel.exec.fetchLoginShellEnvVariables()
    return AgentWorkbenchLaunchExecutionContext(
      projectRoot = projectRoot,
      systemDir = resolveSystemDir(eel, environmentVariables),
      osFamily = eelDescriptor.osFamily,
      environmentVariables = environmentVariables,
      targetPathStringResolver = { path -> path.asEelPath().toString() },
    )
  }
}

private fun resolveSystemDir(
  eel: EelApi,
  environmentVariables: Map<String, String>,
): Path {
  val selector = PathManager.getPathsSelector() ?: "IJ-Platform"
  val userHomeFolder = eel.userInfo.home.asNioPath().toString()
  return Path.of(PathManager.getDefaultSystemPathFor(eel.platform.toOs(), userHomeFolder, selector, environmentVariables))
}
