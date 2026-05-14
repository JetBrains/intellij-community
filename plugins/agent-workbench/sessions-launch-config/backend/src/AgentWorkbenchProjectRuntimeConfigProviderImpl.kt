// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.launch.config.backend

import com.intellij.agent.workbench.common.parseAgentWorkbenchPathOrNull
import com.intellij.agent.workbench.sessions.core.config.AgentWorkbenchProjectRuntimeConfigProvider

internal class AgentWorkbenchProjectRuntimeConfigProviderImpl(
  private val projectConfigCache: AgentWorkbenchProjectLaunchConfigCache = AgentWorkbenchProjectLaunchConfigCache.shared,
) : AgentWorkbenchProjectRuntimeConfigProvider {
  override fun isRefreshVfsOnStatusUpdatesEnabled(projectRoot: String): Boolean {
    val parsedProjectRoot = parseAgentWorkbenchPathOrNull(projectRoot)?.normalize() ?: return true
    return projectConfigCache.isRefreshVfsOnStatusUpdatesEnabled(parsedProjectRoot)
  }
}
