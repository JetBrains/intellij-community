// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.providers

import com.intellij.agent.workbench.sessions.AgentSessionProvider
import com.intellij.agent.workbench.sessions.AgentSessionThread
import com.intellij.openapi.project.Project

abstract class BaseAgentSessionSource(
  final override val provider: AgentSessionProvider,
  final override val canReportExactThreadCount: Boolean = true,
) : AgentSessionSource {
  final override suspend fun listThreadsFromOpenProject(path: String, project: Project): List<AgentSessionThread> {
    return listThreads(path = path, openProject = project)
  }

  final override suspend fun listThreadsFromClosedProject(path: String): List<AgentSessionThread> {
    return listThreads(path = path, openProject = null)
  }

  protected abstract suspend fun listThreads(path: String, openProject: Project?): List<AgentSessionThread>
}
