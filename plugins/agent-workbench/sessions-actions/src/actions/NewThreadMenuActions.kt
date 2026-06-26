// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.actions

// @spec community/plugins/agent-workbench/spec/sessions/agent-terminal-sessions.spec.md
// @spec community/plugins/agent-workbench/spec/actions/new-thread.spec.md

import com.intellij.agent.workbench.chat.AgentChatDeferredStartContent
import com.intellij.agent.workbench.prompt.core.AgentPromptInvocationData
import com.intellij.agent.workbench.prompt.core.AgentPromptLaunchProfile
import com.intellij.agent.workbench.prompt.core.AgentPromptLaunchRequest
import com.intellij.agent.workbench.prompt.core.AgentPromptLaunchResult
import com.intellij.agent.workbench.prompt.core.AgentPromptLauncherBridge
import com.intellij.agent.workbench.prompt.core.AgentPromptLaunchers
import com.intellij.agent.workbench.prompt.core.AgentPromptProjectPathCandidate
import com.intellij.agent.workbench.prompt.core.AgentPromptReusableSourceEntry
import com.intellij.agent.workbench.prompt.ui.emptyState.createAgentWorkbenchInlineNewThreadPromptComponent
import com.intellij.agent.workbench.sessions.AgentSessionsBundle
import com.intellij.agent.workbench.sessions.projectLabelForPath
import com.intellij.platform.ai.agent.sessions.core.providers.initialMessageRequestForLaunchProfile
import com.intellij.agent.workbench.sessions.statistics.AgentWorkbenchEntryPoint
import com.intellij.agent.workbench.sessions.service.AgentDeferredNewSessionHandle
import com.intellij.agent.workbench.sessions.service.AgentSessionLaunchService
import com.intellij.agent.workbench.ui.AgentWorkbenchActionIds
import com.intellij.platform.ai.agent.core.normalizeAgentWorkbenchPath
import com.intellij.platform.ai.agent.core.session.AgentSessionProvider
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionProviderDescriptor
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionProviders
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.registry.RegistryManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal const val AGENT_WORKBENCH_NEW_THREAD_INLINE_PROMPT_REGISTRY_KEY: String = "agent.workbench.new.thread.inline.prompt"

fun createNewThreadViaService(
  path: String,
  profile: AgentPromptLaunchProfile,
  currentProject: Project,
  entryPoint: AgentWorkbenchEntryPoint,
) {
  val provider = AgentSessionProvider.from(profile.providerId)
  val descriptor = AgentSessionProviders.find(provider)
  if (!shouldOpenInlineNewThreadPrompt(descriptor)) {
    createNewThreadDirectly(path = path, profile = profile, currentProject = currentProject, entryPoint = entryPoint)
    return
  }
  service<AgentSessionsInlineNewThreadPromptService>().openInlinePrompt(
    path = path,
    profile = profile,
    currentProject = currentProject,
    entryPoint = entryPoint,
    descriptor = checkNotNull(descriptor),
  )
}

private fun shouldOpenInlineNewThreadPrompt(descriptor: AgentSessionProviderDescriptor?): Boolean {
  return shouldOpenInlineNewThreadPrompt(
    registryEnabled = RegistryManager.getInstance().get(AGENT_WORKBENCH_NEW_THREAD_INLINE_PROMPT_REGISTRY_KEY).asBoolean(),
    descriptor = descriptor,
  )
}

internal fun shouldOpenInlineNewThreadPrompt(
  registryEnabled: Boolean,
  descriptor: AgentSessionProviderDescriptor?,
): Boolean = registryEnabled && descriptor?.supportsPromptLaunch == true

private fun createNewThreadDirectly(
  path: String,
  profile: AgentPromptLaunchProfile,
  currentProject: Project,
  entryPoint: AgentWorkbenchEntryPoint,
) {
  service<AgentSessionLaunchService>().createNewSession(
    path = path,
    launchProfileId = profile.id,
    entryPoint = entryPoint,
    currentProject = currentProject,
    initialMessageRequest = initialMessageRequestForLaunchProfile(profile),
  )
}

@Service(Service.Level.APP)
internal class AgentSessionsInlineNewThreadPromptService internal constructor(
  private val coroutineScope: CoroutineScope,
) {
  fun openInlinePrompt(
    path: String,
    profile: AgentPromptLaunchProfile,
    currentProject: Project,
    entryPoint: AgentWorkbenchEntryPoint,
    descriptor: AgentSessionProviderDescriptor,
  ) {
    coroutineScope.launch(CoroutineName("Agent Workbench inline New Thread prompt")) {
      openInlinePromptSuspending(
        path = path,
        profile = profile,
        currentProject = currentProject,
        entryPoint = entryPoint,
        descriptor = descriptor,
      )
    }
  }

  private suspend fun openInlinePromptSuspending(
    path: String,
    profile: AgentPromptLaunchProfile,
    currentProject: Project,
    entryPoint: AgentWorkbenchEntryPoint,
    descriptor: AgentSessionProviderDescriptor,
  ) {
    val normalizedPath = normalizeAgentWorkbenchPath(path)
    val provider = AgentSessionProvider.from(profile.providerId)
    val handleDeferred = CompletableDeferred<AgentDeferredNewSessionHandle>()
    val handle = try {
      serviceAsync<AgentSessionLaunchService>().createDeferredNewSession(
        path = normalizedPath,
        provider = provider,
        mode = profile.launchMode,
        entryPoint = entryPoint,
        launchProfileId = profile.id,
        generationSettings = profile.generationSettings,
        waitingTitle = AgentSessionsBundle.message("toolwindow.thread.preparing.title", providerDisplayName(descriptor)),
        waitingMessage = AgentSessionsBundle.message("toolwindow.thread.preparing.body"),
        deferredStartContentProvider = { project ->
          createInlinePromptContent(
            project = project,
            path = normalizedPath,
            profile = profile,
            entryPoint = entryPoint,
            handleDeferred = handleDeferred,
          )
        },
      ).handle
    }
    catch (e: CancellationException) {
      handleDeferred.cancel()
      throw e
    }
    catch (e: Throwable) {
      handleDeferred.cancel()
      LOG.warn("Failed to open inline New Thread prompt for ${profile.providerId}:$normalizedPath", e)
      createNewThreadDirectly(path = normalizedPath, profile = profile, currentProject = currentProject, entryPoint = entryPoint)
      return
    }

    if (handle == null) {
      handleDeferred.cancel()
      createNewThreadDirectly(path = normalizedPath, profile = profile, currentProject = currentProject, entryPoint = entryPoint)
      return
    }
    handleDeferred.complete(handle)
  }

  private fun createInlinePromptContent(
    project: Project,
    path: String,
    profile: AgentPromptLaunchProfile,
    entryPoint: AgentWorkbenchEntryPoint,
    handleDeferred: CompletableDeferred<AgentDeferredNewSessionHandle>,
  ): AgentChatDeferredStartContent {
    val launcher = InlineNewThreadPromptLauncherBridge(projectPath = path, handleProvider = { handleDeferred.await() })
    val component = createAgentWorkbenchInlineNewThreadPromptComponent(
      project = project,
      invocationData = AgentPromptInvocationData(
        project = project,
        actionId = AgentWorkbenchActionIds.Sessions.MainToolbar.NEW_THREAD,
        actionText = AgentSessionsBundle.message("action.AgentWorkbenchSessions.MainToolbar.NewThread.text"),
        actionPlace = entryPoint.name,
        invokedAtMs = System.currentTimeMillis(),
      ),
      launcherProvider = { launcher },
      initialLaunchProfileId = profile.id,
    )
    return AgentChatDeferredStartContent(
      component = component,
      preferredFocusedComponent = component.preferredFocusedComponent,
      disposeContent = { Disposer.dispose(component) },
    )
  }
}

private class InlineNewThreadPromptLauncherBridge(
  private val projectPath: String,
  private val handleProvider: suspend () -> AgentDeferredNewSessionHandle,
  private val delegateProvider: () -> AgentPromptLauncherBridge? = AgentPromptLaunchers::find,
) : AgentPromptLauncherBridge {
  override suspend fun launch(request: AgentPromptLaunchRequest): AgentPromptLaunchResult {
    return handleProvider().launch(request)
  }

  override fun loadProviderPreferences(): AgentPromptLauncherBridge.ProviderPreferences {
    return delegateProvider()?.loadProviderPreferences() ?: AgentPromptLauncherBridge.ProviderPreferences()
  }

  override fun saveProviderPreferences(preferences: AgentPromptLauncherBridge.ProviderPreferences) {
    delegateProvider()?.saveProviderPreferences(preferences)
  }

  override fun resolveWorkingProjectPath(invocationData: AgentPromptInvocationData): String {
    return projectPath
  }

  override fun resolveSourceProject(invocationData: AgentPromptInvocationData): Project {
    return delegateProvider()?.resolveSourceProject(invocationData) ?: invocationData.project
  }

  override fun listWorkingProjectPathCandidates(invocationData: AgentPromptInvocationData): List<AgentPromptProjectPathCandidate> {
    return listOf(
      AgentPromptProjectPathCandidate(
        path = projectPath,
        displayName = projectLabelForPath(projectPath),
      )
    )
  }

  override suspend fun listReusablePromptSourceEntries(
    projectPath: String,
    provider: AgentSessionProvider,
  ): List<AgentPromptReusableSourceEntry> {
    return delegateProvider()?.listReusablePromptSourceEntries(projectPath, provider).orEmpty()
  }
}

private fun providerDisplayName(descriptor: AgentSessionProviderDescriptor): @NlsSafe String {
  return runCatching { AgentSessionsBundle.message(descriptor.displayNameKey) }
    .getOrDefault(descriptor.displayNameFallback)
}

private val LOG = logger<AgentSessionsInlineNewThreadPromptService>()
