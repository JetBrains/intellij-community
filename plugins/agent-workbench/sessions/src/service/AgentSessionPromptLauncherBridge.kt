// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.service

import com.intellij.agent.workbench.chat.addContextToOpenTopLevelAgentChat
import com.intellij.agent.workbench.chat.collectOpenAgentChatAddContextTargetCandidates
import com.intellij.agent.workbench.common.normalizeAgentWorkbenchPath
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.prompt.core.AGENT_PROMPT_INVOCATION_DATA_CONTEXT_KEY
import com.intellij.agent.workbench.prompt.core.AgentPromptAddContextTargetCandidate
import com.intellij.agent.workbench.prompt.core.AgentPromptAddContextToTargetRequest
import com.intellij.agent.workbench.prompt.core.AgentPromptAddContextToTargetResult
import com.intellij.agent.workbench.prompt.core.AgentPromptContainerLauncher
import com.intellij.agent.workbench.prompt.core.AgentPromptExistingThreadsSnapshot
import com.intellij.agent.workbench.prompt.core.AgentPromptInvocationData
import com.intellij.agent.workbench.prompt.core.AgentPromptLaunchError
import com.intellij.agent.workbench.prompt.core.AgentPromptLaunchRequest
import com.intellij.agent.workbench.prompt.core.AgentPromptLaunchResult
import com.intellij.agent.workbench.prompt.core.AgentPromptLauncherBridge
import com.intellij.agent.workbench.prompt.core.AgentPromptProjectPathCandidate
import com.intellij.agent.workbench.prompt.core.AgentPromptReusableSourceEntry
import com.intellij.agent.workbench.prompt.core.getAgentPromptProjectPathContext
import com.intellij.agent.workbench.sessions.frame.AgentWorkbenchDedicatedFrameProjectManager
import com.intellij.agent.workbench.sessions.model.AgentSessionsState
import com.intellij.agent.workbench.sessions.model.sortAgentSessionThreadsForDisplay
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviders
import com.intellij.agent.workbench.sessions.core.statistics.AgentWorkbenchTelemetry
import com.intellij.agent.workbench.sessions.state.AgentSessionUiPreferencesStateService
import com.intellij.agent.workbench.sessions.util.isAgentSessionNewSessionId
import com.intellij.ide.RecentProjectsManager
import com.intellij.ide.RecentProjectsManagerBase
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

internal class AgentSessionPromptLauncherBridge : AgentPromptLauncherBridge {
  private val launchPromptRequest: (AgentPromptLaunchRequest) -> AgentPromptLaunchResult
  private val stateFlowProvider: () -> StateFlow<AgentSessionsState>
  private val pathStateResolver: (AgentSessionsState, String) -> AgentSessionPathState?
  private val refreshCatalogAndLoadNewlyOpened: () -> Unit
  private val refreshProviderForPath: (String, AgentSessionProvider) -> Unit
  private val preferredProviderProvider: () -> AgentSessionProvider?
  private val sourceProjectResolver: (String) -> Project?
  private val providerPreferencesLoader: () -> AgentPromptLauncherBridge.ProviderPreferences
  private val providerPreferencesSaver: (AgentPromptLauncherBridge.ProviderPreferences) -> Unit
  private val addContextToOpenChatTargetHandler: suspend (AgentPromptAddContextToTargetRequest) -> AgentPromptAddContextToTargetResult

  @Suppress("unused")
  constructor() : this(
    launchPromptRequest = { request -> service<AgentSessionLaunchService>().launchPromptRequest(request) },
    stateFlowProvider = { service<AgentSessionReadService>().stateFlow() },
    pathStateResolver = ::resolveAgentSessionPathState,
    refreshCatalogAndLoadNewlyOpened = { service<AgentSessionRefreshService>().refreshCatalogAndLoadNewlyOpened() },
    refreshProviderForPath = { path, provider ->
      service<AgentSessionRefreshService>().refreshProviderForPath(path = path,
                                                                   provider = provider)
    },
    preferredProviderProvider = { service<AgentSessionUiPreferencesStateService>().getLastUsedProvider() },
    providerPreferencesLoader = { service<AgentSessionUiPreferencesStateService>().getProviderPreferences() },
    providerPreferencesSaver = { prefs -> service<AgentSessionUiPreferencesStateService>().setProviderPreferences(prefs) },
    sourceProjectResolver = ::findOpenSourceProjectByPath,
    addContextToOpenChatTarget = ::addContextItemsToOpenChatTarget,
  )

  internal constructor(
    launchPromptRequest: (AgentPromptLaunchRequest) -> AgentPromptLaunchResult,
  ) : this(
    launchPromptRequest = launchPromptRequest,
    stateFlowProvider = {
      error("stateFlowProvider is unavailable in this test setup")
    },
    pathStateResolver = ::resolveAgentSessionPathState,
    refreshCatalogAndLoadNewlyOpened = {},
    refreshProviderForPath = { _, _ -> },
    preferredProviderProvider = { null },
    sourceProjectResolver = ::findOpenSourceProjectByPath,
    providerPreferencesLoader = { AgentPromptLauncherBridge.ProviderPreferences() },
    providerPreferencesSaver = {},
    addContextToOpenChatTarget = ::addContextItemsToOpenChatTarget,
  )

  internal constructor(
    launchPromptRequest: (AgentPromptLaunchRequest) -> AgentPromptLaunchResult,
    stateFlowProvider: () -> StateFlow<AgentSessionsState>,
    pathStateResolver: (AgentSessionsState, String) -> AgentSessionPathState?,
    refreshCatalogAndLoadNewlyOpened: () -> Unit,
    refreshProviderForPath: (String, AgentSessionProvider) -> Unit,
    preferredProviderProvider: () -> AgentSessionProvider?,
    sourceProjectResolver: (String) -> Project? = ::findOpenSourceProjectByPath,
    providerPreferencesLoader: () -> AgentPromptLauncherBridge.ProviderPreferences = { AgentPromptLauncherBridge.ProviderPreferences() },
    providerPreferencesSaver: (AgentPromptLauncherBridge.ProviderPreferences) -> Unit = {},
    addContextToOpenChatTarget: suspend (AgentPromptAddContextToTargetRequest) -> AgentPromptAddContextToTargetResult = ::addContextItemsToOpenChatTarget,
  ) {
    this.launchPromptRequest = launchPromptRequest
    this.stateFlowProvider = stateFlowProvider
    this.pathStateResolver = pathStateResolver
    this.refreshCatalogAndLoadNewlyOpened = refreshCatalogAndLoadNewlyOpened
    this.refreshProviderForPath = refreshProviderForPath
    this.preferredProviderProvider = preferredProviderProvider
    this.sourceProjectResolver = sourceProjectResolver
    this.providerPreferencesLoader = providerPreferencesLoader
    this.providerPreferencesSaver = providerPreferencesSaver
    this.addContextToOpenChatTargetHandler = addContextToOpenChatTarget
  }

  override fun launch(request: AgentPromptLaunchRequest): AgentPromptLaunchResult {
    fun reportPromptLaunchResolved(result: AgentPromptLaunchResult): AgentPromptLaunchResult {
      AgentWorkbenchTelemetry.logPromptLaunchResolved(request, result)
      return result
    }

    if (request.containerMode) {
      val containerLauncher = AgentPromptContainerLauncher.findInstance()
      if (containerLauncher == null) {
        return reportPromptLaunchResolved(AgentPromptLaunchResult.failure(AgentPromptLaunchError.UNSUPPORTED_LAUNCH_MODE))
      }
      if (!containerLauncher.supportsProvider(request.provider)) {
        return reportPromptLaunchResolved(AgentPromptLaunchResult.failure(AgentPromptLaunchError.UNSUPPORTED_LAUNCH_MODE))
      }
      if (!containerLauncher.isAvailable()) {
        return reportPromptLaunchResolved(AgentPromptLaunchResult.failure(AgentPromptLaunchError.PROVIDER_UNAVAILABLE))
      }
      val project = sourceProjectResolver(request.projectPath)
      if (project == null) {
        return reportPromptLaunchResolved(AgentPromptLaunchResult.failure(AgentPromptLaunchError.INTERNAL_ERROR))
      }
      containerLauncher.launch(project, request)
      return AgentPromptLaunchResult.SUCCESS
    }
    return launchPromptRequest(request)
  }

  override fun preferredProvider(): AgentSessionProvider? {
    return preferredProviderProvider()
  }

  override fun loadProviderPreferences(): AgentPromptLauncherBridge.ProviderPreferences {
    return providerPreferencesLoader()
  }

  override fun saveProviderPreferences(preferences: AgentPromptLauncherBridge.ProviderPreferences) {
    providerPreferencesSaver(preferences)
  }

  override fun resolveWorkingProjectPath(invocationData: AgentPromptInvocationData): String? {
    return listWorkingProjectPathCandidates(invocationData).firstOrNull()?.path
  }

  override fun resolveSourceProject(invocationData: AgentPromptInvocationData): Project? {
    val project = invocationData.project
    if (!AgentWorkbenchDedicatedFrameProjectManager.isDedicatedProject(project)) {
      return project
    }

    val projectPath = resolveWorkingProjectPath(invocationData) ?: return null
    return sourceProjectResolver(projectPath)
  }

  override fun listWorkingProjectPathCandidates(invocationData: AgentPromptInvocationData): List<AgentPromptProjectPathCandidate> {
    return buildWorkingProjectPathCandidates(invocationData)
  }

  override suspend fun listAddContextTargetCandidates(projectPath: String): List<AgentPromptAddContextTargetCandidate> {
    return collectOpenAgentChatAddContextTargetCandidates(projectPath)
  }

  override suspend fun addContextToOpenChatTarget(request: AgentPromptAddContextToTargetRequest): AgentPromptAddContextToTargetResult {
    return addContextToOpenChatTargetHandler.invoke(request)
  }

  override suspend fun listReusablePromptSourceEntries(
    projectPath: String,
    provider: AgentSessionProvider,
  ): List<AgentPromptReusableSourceEntry> {
    return AgentSessionProviders.find(provider)?.listReusablePromptSourceEntries(projectPath) ?: emptyList()
  }

  override fun observeExistingThreads(
    projectPath: String,
    provider: AgentSessionProvider,
  ): Flow<AgentPromptExistingThreadsSnapshot> {
    val normalizedPath = normalizeAgentWorkbenchPath(projectPath)
    return stateFlowProvider()
      .map { state ->
        val pathState = pathStateResolver(state, normalizedPath)
        buildSnapshot(pathState = pathState, provider = provider)
      }
      .distinctUntilChanged()
  }

  override suspend fun refreshExistingThreads(projectPath: String, provider: AgentSessionProvider) {
    val normalizedPath = normalizeAgentWorkbenchPath(projectPath)
    val pathState = pathStateResolver(stateFlowProvider().value, normalizedPath)
    when {
      pathState == null -> {
        refreshCatalogAndLoadNewlyOpened()
      }
      pathState.hasProviderSnapshot(provider) -> {
        refreshProviderForPath(normalizedPath, provider)
      }
      !pathState.isProviderLoading(provider) -> {
        refreshCatalogAndLoadNewlyOpened()
      }
    }
  }
}

private fun buildWorkingProjectPathCandidates(invocationData: AgentPromptInvocationData): List<AgentPromptProjectPathCandidate> {
  val project = invocationData.project
  val candidatesByPath = LinkedHashMap<String, AgentPromptProjectPathCandidate>()

  fun addCandidate(path: String?, displayName: String?) {
    val normalizedPath = normalizeOpenableSourceProjectPath(path) ?: return
    if (normalizedPath in candidatesByPath) {
      return
    }
    candidatesByPath[normalizedPath] = AgentPromptProjectPathCandidate(
      path = normalizedPath,
      displayName = displayName
                      ?.takeIf { it.isNotBlank() }
                    ?: normalizedPath.substringAfterLast('/').ifBlank { normalizedPath },
    )
  }

  val currentProjectPath = project.basePath
    ?.takeIf { it.isNotBlank() }
    ?.let(::normalizeAgentWorkbenchPath)
  if (!AgentWorkbenchDedicatedFrameProjectManager.isDedicatedProject(project)) {
    addCandidate(path = currentProjectPath, displayName = project.name)
    return candidatesByPath.values.toList()
  }

  val dataContext = invocationData.dataContextOrNull()
  val promptProjectPathContext = getAgentPromptProjectPathContext(dataContext)
  addCandidate(path = promptProjectPathContext?.path, displayName = promptProjectPathContext?.displayName)

  addCandidate(path = selectedChatSourceProjectPath(project), displayName = null)

  val recentProjectsManager = RecentProjectsManager.getInstance() as? RecentProjectsManagerBase
  recentProjectsManager
    ?.getRecentPaths()
    ?.forEach { recentPath ->
      val normalizedPath = normalizeAgentWorkbenchPath(recentPath)
      val displayName = recentProjectsManager.getDisplayName(normalizedPath)
                          .takeIf { !it.isNullOrBlank() }
                        ?: recentProjectsManager.getProjectName(normalizedPath).takeIf { it.isNotBlank() }
      addCandidate(path = normalizedPath, displayName = displayName)
    }

  return candidatesByPath.values.toList()
}

private suspend fun addContextItemsToOpenChatTarget(
  request: AgentPromptAddContextToTargetRequest,
): AgentPromptAddContextToTargetResult {
  val target = request.target
  return addContextToOpenTopLevelAgentChat(
    projectPath = target.projectPath,
    provider = target.provider,
    threadId = target.threadId,
    contextItems = request.contextItems,
  )
}

private fun AgentPromptInvocationData.dataContextOrNull(): DataContext? {
  return attributes[AGENT_PROMPT_INVOCATION_DATA_CONTEXT_KEY] as? DataContext
}

private fun buildSnapshot(pathState: AgentSessionPathState?, provider: AgentSessionProvider): AgentPromptExistingThreadsSnapshot {
  if (pathState == null) {
    return AgentPromptExistingThreadsSnapshot(
      threads = emptyList(),
      isLoading = false,
      hasLoaded = false,
      hasError = false,
    )
  }

  val providerThreads = pathState.threads
    .asSequence()
    .filter { thread -> thread.provider == provider }
    .filter { thread -> !thread.archived }
    .filter { thread -> !isAgentSessionNewSessionId(thread.id) }
    .toList()
    .let(::sortAgentSessionThreadsForDisplay)
  val hasProviderWarning = pathState.providerWarnings.any { warning -> warning.provider == provider }
  val isProviderLoading = pathState.isProviderLoading(provider)
  val hasProviderSnapshot = pathState.hasProviderSnapshot(provider)
  val hasError = pathState.errorMessage != null ||
                 (hasProviderWarning && providerThreads.isEmpty() && hasProviderSnapshot && !isProviderLoading)

  return AgentPromptExistingThreadsSnapshot(
    threads = providerThreads,
    isLoading = isProviderLoading,
    hasLoaded = hasProviderSnapshot,
    hasError = hasError,
  )
}
