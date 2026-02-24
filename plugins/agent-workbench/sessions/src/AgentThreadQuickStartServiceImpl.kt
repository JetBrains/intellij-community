// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import com.intellij.agent.workbench.sessions.core.AgentSessionLaunchMode
import com.intellij.agent.workbench.sessions.core.AgentSessionProvider
import com.intellij.agent.workbench.sessions.core.AgentThreadQuickStartService
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderBridge
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderBridges
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project

internal class AgentThreadQuickStartServiceImpl : AgentThreadQuickStartService {
  private val isDedicatedProject: (Project) -> Boolean
  private val getLastUsedProvider: () -> AgentSessionProvider?
  private val findBridge: (AgentSessionProvider) -> AgentSessionProviderBridge?
  private val allBridges: () -> List<AgentSessionProviderBridge>
  private val createNewSession: (String, AgentSessionProvider, Project) -> Unit

  @Suppress("unused")
  constructor() {
    isDedicatedProject = AgentWorkbenchDedicatedFrameProjectManager::isDedicatedProject
    getLastUsedProvider = { service<AgentSessionsTreeUiStateService>().getLastUsedProvider() }
    findBridge = AgentSessionProviderBridges::find
    allBridges = AgentSessionProviderBridges::allBridges
    createNewSession = { path, provider, project ->
      service<AgentSessionsService>().createNewSession(
        path = path,
        provider = provider,
        mode = AgentSessionLaunchMode.STANDARD,
        currentProject = project,
      )
    }
  }

  internal constructor(
    isDedicatedProject: (Project) -> Boolean,
    getLastUsedProvider: () -> AgentSessionProvider?,
    findBridge: (AgentSessionProvider) -> AgentSessionProviderBridge?,
    allBridges: () -> List<AgentSessionProviderBridge>,
    createNewSession: (String, AgentSessionProvider, Project) -> Unit,
  ) {
    this.isDedicatedProject = isDedicatedProject
    this.getLastUsedProvider = getLastUsedProvider
    this.findBridge = findBridge
    this.allBridges = allBridges
    this.createNewSession = createNewSession
  }

  override fun isVisible(project: Project): Boolean {
    return isDedicatedProject(project)
  }

  override fun isEnabled(project: Project): Boolean {
    return isVisible(project) && resolveLaunchProvider() != null
  }

  override fun startNewThread(path: String, project: Project) {
    if (!isVisible(project)) {
      return
    }
    val provider = resolveLaunchProvider() ?: return
    createNewSession(path, provider, project)
  }

  private fun resolveLaunchProvider(): AgentSessionProvider? {
    val lastUsedProvider = getLastUsedProvider()
    if (lastUsedProvider != null) {
      val bridge = findBridge(lastUsedProvider)
      if (bridge != null && bridge.canCreateStandardSession()) {
        return lastUsedProvider
      }
    }
    return allBridges().firstOrNull { it.canCreateStandardSession() }?.provider
  }
}

private fun AgentSessionProviderBridge.canCreateStandardSession(): Boolean {
  return AgentSessionLaunchMode.STANDARD in supportedLaunchModes
}
