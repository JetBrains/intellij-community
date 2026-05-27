// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.launch.config.backend

import com.intellij.platform.ai.agent.core.parseAgentWorkbenchPathOrNull
import com.intellij.platform.ai.agent.sessions.core.config.AgentWorkbenchProjectRuntimeConfigProvider
import com.intellij.platform.ai.agent.sessions.core.paths.resolveAgentWorkbenchProjectDirectory

internal class AgentWorkbenchProjectRuntimeConfigProviderImpl(
  private val projectConfigCache: AgentWorkbenchProjectLaunchConfigCache = AgentWorkbenchProjectLaunchConfigCache.shared,
) : AgentWorkbenchProjectRuntimeConfigProvider {
  override fun isRefreshVfsOnStatusUpdatesEnabled(projectRoot: String): Boolean {
    val projectDirectory = resolveAgentWorkbenchProjectDirectory(identityPath = projectRoot) ?: projectRoot
    val parsedProjectRoot = parseAgentWorkbenchPathOrNull(projectDirectory)?.normalize() ?: return true
    return projectConfigCache.isRefreshVfsOnStatusUpdatesEnabled(parsedProjectRoot)
  }
}
