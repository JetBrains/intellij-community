// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.actions

// @spec community/plugins/agent-workbench/spec/sessions/agent-terminal-sessions.spec.md
// @spec community/plugins/agent-workbench/spec/actions/new-thread.spec.md

import com.intellij.agent.workbench.thread.view.AgentThreadViewDeferredStartContent
import com.intellij.agent.workbench.prompt.core.AgentPromptContextEnvelopeFormatter
import com.intellij.agent.workbench.prompt.core.AgentPromptContextItem
import com.intellij.agent.workbench.prompt.core.AgentPromptContextResolverService
import com.intellij.agent.workbench.prompt.core.AgentPromptInitialMessageRequest
import com.intellij.agent.workbench.prompt.core.AgentPromptInvocationData
import com.intellij.agent.workbench.prompt.core.AgentPromptLaunchProfile
import com.intellij.agent.workbench.prompt.core.AgentPromptLaunchRequest
import com.intellij.agent.workbench.prompt.core.AgentPromptLaunchResult
import com.intellij.agent.workbench.prompt.core.AgentPromptLauncherBridge
import com.intellij.agent.workbench.prompt.core.AgentPromptLaunchers
import com.intellij.agent.workbench.prompt.core.AgentPromptProjectPathCandidate
import com.intellij.agent.workbench.prompt.core.AgentPromptReusableSourceEntry
import com.intellij.agent.workbench.prompt.ui.emptyState.createAgentWorkbenchInlinePromptEditorHost
import com.intellij.agent.workbench.prompt.ui.emptyState.createAgentWorkbenchInlineNewThreadPromptComponent
import com.intellij.agent.workbench.sessions.AgentSessionsBundle
import com.intellij.agent.workbench.sessions.projectLabelForPath
import com.intellij.agent.workbench.sessions.newThreadActionText
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
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.RegistryManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.annotations.Nls

internal const val AGENT_WORKBENCH_NEW_THREAD_INLINE_PROMPT_REGISTRY_KEY: String = "agent.workbench.new.thread.inline.prompt"

fun createNewThreadViaService(
  path: String,
  profile: AgentPromptLaunchProfile,
  currentProject: Project,
  entryPoint: AgentWorkbenchEntryPoint,
) {
  createNewThreadViaService(
    path = path,
    profile = profile,
    currentProject = currentProject,
    entryPoint = entryPoint,
    invocationData = null,
  )
}

internal fun createNewThreadViaService(
  path: String,
  profile: AgentPromptLaunchProfile,
  currentProject: Project,
  entryPoint: AgentWorkbenchEntryPoint,
  invocationData: AgentPromptInvocationData?,
) {
  val provider = AgentSessionProvider.from(profile.providerId)
  val descriptor = AgentSessionProviders.find(provider)
  if (!shouldOpenInlineNewThreadPrompt(descriptor = descriptor)) {
    createNewThreadDirectly(
      path = path,
      profile = profile,
      currentProject = currentProject,
      entryPoint = entryPoint,
      invocationData = invocationData,
    )
    return
  }
  service<AgentSessionsInlineNewThreadPromptService>().openInlinePrompt(
    path = path,
    profile = profile,
    currentProject = currentProject,
    entryPoint = entryPoint,
    invocationData = invocationData,
  )
}

private fun shouldOpenInlineNewThreadPrompt(
  descriptor: AgentSessionProviderDescriptor?,
): Boolean {
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
  invocationData: AgentPromptInvocationData?,
) {
  val normalizedPath = normalizeAgentWorkbenchPath(path)
  service<AgentSessionLaunchService>().createNewSession(
    path = normalizedPath,
    launchProfileId = profile.id,
    entryPoint = entryPoint,
    currentProject = currentProject,
    initialMessageRequest = buildNewThreadInitialMessageRequest(
      profile = profile,
      projectPath = normalizedPath,
      invocationData = invocationData,
    ),
  )
}

internal fun buildNewThreadInitialMessageRequest(
  profile: AgentPromptLaunchProfile,
  projectPath: String,
  invocationData: AgentPromptInvocationData?,
  collectDefaultContext: (AgentPromptInvocationData) -> List<AgentPromptContextItem> = ::collectDefaultNewThreadContext,
): AgentPromptInitialMessageRequest {
  val baseRequest = initialMessageRequestForLaunchProfile(profile)
  val contextItems = invocationData?.let(collectDefaultContext).orEmpty()
  if (contextItems.isEmpty()) {
    return baseRequest
  }

  val normalizedProjectPath = normalizeAgentWorkbenchPath(projectPath)
  val selection = AgentPromptContextEnvelopeFormatter.prepareContextEnvelopeSelection(
    items = contextItems,
    softCapChars = AgentPromptContextEnvelopeFormatter.DEFAULT_SOFT_CAP_CHARS,
    projectPath = normalizedProjectPath,
  )
  val preparedSelection = if (selection.exceedsSoftCap) {
    AgentPromptContextEnvelopeFormatter.autoTrimContextEnvelopeSelection(
      selection = selection,
      projectPath = normalizedProjectPath,
    )
  }
  else {
    selection
  }
  return baseRequest.copy(
    projectPath = normalizedProjectPath,
    contextItems = preparedSelection.items,
    contextEnvelopeSummary = preparedSelection.summary,
  )
}

private fun collectDefaultNewThreadContext(invocationData: AgentPromptInvocationData): List<AgentPromptContextItem> {
  return invocationData.project.service<AgentPromptContextResolverService>().collectDefaultContext(invocationData)
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
    invocationData: AgentPromptInvocationData?,
  ) {
    coroutineScope.launch(Dispatchers.EDT + CoroutineName("Agent Workbench inline New Thread prompt")) {
      openInlinePromptSuspending(
        path = path,
        profile = profile,
        currentProject = currentProject,
        entryPoint = entryPoint,
        invocationData = invocationData,
      )
    }
  }

  private suspend fun openInlinePromptSuspending(
    path: String,
    profile: AgentPromptLaunchProfile,
    currentProject: Project,
    entryPoint: AgentWorkbenchEntryPoint,
    invocationData: AgentPromptInvocationData?,
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
        launchTargetId = profile.launchTargetId,
        generationSettings = profile.generationSettings,
        waitingTitle = AgentSessionsBundle.message("toolwindow.thread.preparing.title"),
        deferredStartContentProvider = { project ->
          createInlinePromptContent(
            project = project,
            path = normalizedPath,
            profile = profile,
            entryPoint = entryPoint,
            handleDeferred = handleDeferred,
            invocationData = invocationData,
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
      createNewThreadDirectly(
        path = normalizedPath,
        profile = profile,
        currentProject = currentProject,
        entryPoint = entryPoint,
        invocationData = invocationData,
      )
      return
    }

    if (handle == null) {
      handleDeferred.cancel()
      createNewThreadDirectly(
        path = normalizedPath,
        profile = profile,
        currentProject = currentProject,
        entryPoint = entryPoint,
        invocationData = invocationData,
      )
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
    invocationData: AgentPromptInvocationData?,
  ): AgentThreadViewDeferredStartContent {
      val launcher = InlineNewThreadPromptLauncherBridge(projectPath = path, handleProvider = { handleDeferred.await() })
      val component = createAgentWorkbenchInlineNewThreadPromptComponent(
        project = project,
        invocationData = (invocationData ?: defaultNewThreadInvocationData(project, entryPoint, profile.name)).copy(project = project),
        launcherProvider = { launcher },
        initialLaunchProfileId = profile.id,
      )
    return AgentThreadViewDeferredStartContent(
      component = createAgentWorkbenchInlinePromptEditorHost(component),
      preferredFocusedComponent = component.preferredFocusedComponent,
      disposeContent = { Disposer.dispose(component) },
    )
  }
}

private fun defaultNewThreadInvocationData(
  project: Project,
  entryPoint: AgentWorkbenchEntryPoint,
  profileName: @Nls String,
): AgentPromptInvocationData {
  return AgentPromptInvocationData(
    project = project,
    actionId = AgentWorkbenchActionIds.Sessions.MainToolbar.NEW_THREAD,
    actionText = newThreadActionText(profileName),
    actionPlace = entryPoint.name,
    invokedAtMs = System.currentTimeMillis(),
  )
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

private val LOG = logger<AgentSessionsInlineNewThreadPromptService>()
