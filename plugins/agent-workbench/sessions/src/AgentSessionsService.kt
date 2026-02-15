// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

// @spec community/plugins/agent-workbench/spec/agent-sessions.spec.md
// @spec community/plugins/agent-workbench/spec/agent-dedicated-frame.spec.md
// @spec community/plugins/agent-workbench/spec/actions/new-thread.spec.md

import com.intellij.agent.workbench.chat.AgentChatEditorService
import com.intellij.agent.workbench.codex.common.CodexCliNotFoundException
import com.intellij.agent.workbench.codex.sessions.SharedCodexAppServerService
import com.intellij.agent.workbench.sessions.providers.AgentSessionSource
import com.intellij.agent.workbench.sessions.providers.codex.CodexCliCommands
import com.intellij.agent.workbench.sessions.providers.createDefaultAgentSessionSources
import com.intellij.ide.RecentProjectsManager
import com.intellij.ide.RecentProjectsManagerBase
import com.intellij.ide.impl.OpenProjectTask
import com.intellij.ide.impl.ProjectUtilService
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.openapi.project.currentOrDefaultProject
import com.intellij.openapi.ui.DoNotAskOption
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.io.FileUtilRt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.nio.file.InvalidPathException
import java.nio.file.Path
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.name

private val LOG = logger<AgentSessionsService>()

private const val SUPPRESS_BRANCH_MISMATCH_DIALOG_KEY = "agent.workbench.suppress.branch.mismatch.dialog"
private const val OPEN_PROJECT_ACTION_KEY_PREFIX = "project-open"
private const val CREATE_SESSION_ACTION_KEY_PREFIX = "session-create"
private const val OPEN_THREAD_ACTION_KEY_PREFIX = "thread-open"
private const val OPEN_SUB_AGENT_ACTION_KEY_PREFIX = "subagent-open"

@Service(Service.Level.APP)
internal class AgentSessionsService private constructor(
  private val serviceScope: CoroutineScope,
  private val sessionSources: List<AgentSessionSource>,
  private val projectEntriesProvider: suspend (AgentSessionsService) -> List<ProjectEntry>,
  subscribeToProjectLifecycle: Boolean,
) {
  @Suppress("unused")
  constructor(serviceScope: CoroutineScope) : this(
    serviceScope = serviceScope,
    sessionSources = createDefaultAgentSessionSources(),
    projectEntriesProvider = { service -> service.collectProjects() },
    subscribeToProjectLifecycle = true,
  )

  internal constructor(
    serviceScope: CoroutineScope,
    sessionSources: List<AgentSessionSource>,
    projectEntriesProvider: suspend () -> List<ProjectEntry>,
    subscribeToProjectLifecycle: Boolean = false,
  ) : this(
    serviceScope = serviceScope,
    sessionSources = sessionSources,
    projectEntriesProvider = { _ -> projectEntriesProvider() },
    subscribeToProjectLifecycle = subscribeToProjectLifecycle,
  )

  private val refreshMutex = Mutex()
  private val onDemandMutex = Mutex()
  private val actionGate = SingleFlightActionGate()
  private val onDemandLoading = LinkedHashSet<String>()
  private val onDemandWorktreeLoading = LinkedHashSet<String>()

  private val mutableState = MutableStateFlow(AgentSessionsState())
  val state: StateFlow<AgentSessionsState> = mutableState.asStateFlow()

  init {
    if (subscribeToProjectLifecycle) {
      ApplicationManager.getApplication().messageBus.connect(serviceScope)
        .subscribe(ProjectManager.TOPIC, object : ProjectManagerListener {
          @Deprecated("Deprecated in Java")
          @Suppress("removal")
          override fun projectOpened(project: Project) {
            refresh()
          }

          override fun projectClosed(project: Project) {
            refresh()
          }
        })
    }
  }

  fun refresh() {
    serviceScope.launch(Dispatchers.IO) {
      if (!refreshMutex.tryLock()) {
        return@launch
      }
      try {
        val entries = projectEntriesProvider(this@AgentSessionsService)
        val currentProjectsByPath = mutableState.value.projects.associateBy { it.path }
        val initialProjects = entries.map { entry ->
          val existing = currentProjectsByPath[entry.path]
          AgentProjectSessions(
            path = entry.path,
            name = entry.name,
            branch = entry.branch ?: existing?.branch,
            isOpen = entry.project != null,
            isLoading = entry.project != null,
            hasLoaded = existing?.hasLoaded ?: false,
            hasUnknownThreadCount = existing?.hasUnknownThreadCount ?: false,
            threads = existing?.threads ?: emptyList(),
            errorMessage = existing?.errorMessage,
            providerWarnings = existing?.providerWarnings ?: emptyList(),
            worktrees = entry.worktreeEntries.map { wt ->
              val existingWt = existing?.worktrees?.firstOrNull { it.path == wt.path }
              val hasExistingData = existingWt != null && existingWt.threads.isNotEmpty()
              AgentWorktree(
                path = wt.path,
                name = wt.name,
                branch = wt.branch,
                isOpen = wt.project != null,
                isLoading = wt.project != null && hasExistingData,
                hasLoaded = existingWt?.hasLoaded ?: false,
                hasUnknownThreadCount = existingWt?.hasUnknownThreadCount ?: false,
                threads = existingWt?.threads ?: emptyList(),
                errorMessage = existingWt?.errorMessage,
                providerWarnings = existingWt?.providerWarnings ?: emptyList(),
              )
            },
          )
        }
        mutableState.update { it.copy(projects = initialProjects, lastUpdatedAt = System.currentTimeMillis()) }

        // Collect all open paths for prefetching (batch Codex calls)
        val openPaths = entries.flatMap { entry ->
          buildList {
            if (entry.project != null) add(entry.path)
            entry.worktreeEntries.filter { it.project != null }.forEach { add(it.path) }
          }
        }

        // Prefetch from all sources in parallel
        val prefetchedByProvider = coroutineScope {
          sessionSources.map { source ->
            async {
              source.provider to try {
                source.prefetchThreads(openPaths)
              }
              catch (_: Throwable) {
                emptyMap()
              }
            }
          }.awaitAll().toMap()
        }

        // Load each (project × source) independently so fast sources (Claude)
        // update the UI immediately without waiting for slow sources (Codex).
        coroutineScope {
          for (entry in entries) {
            launch {
              if (entry.project == null) {
                updateProject(entry.path) { it.copy(isLoading = false) }
                return@launch
              }
              val sourceResults = java.util.concurrent.CopyOnWriteArrayList<AgentSessionSourceLoadResult>()
              coroutineScope {
                for (source in sessionSources) {
                  launch {
                    val sourceResult = try {
                      val prefetched = prefetchedByProvider[source.provider]?.get(entry.path)
                      val threads = prefetched
                                    ?: source.listThreadsFromOpenProject(path = entry.path, project = entry.project)
                      AgentSessionSourceLoadResult(
                        provider = source.provider,
                        result = Result.success(threads),
                        hasUnknownTotal = !source.canReportExactThreadCount,
                      )
                    }
                    catch (e: Throwable) {
                      if (e is CancellationException) throw e
                      LOG.warn("Failed to load ${source.provider.name} sessions for ${entry.path}", e)
                      AgentSessionSourceLoadResult(
                        provider = source.provider,
                        result = Result.failure(e),
                      )
                    }
                    sourceResults.add(sourceResult)
                    // Incremental UI update — clear spinner as soon as any source succeeds
                    val partial = mergeAgentSessionSourceLoadResults(
                      sourceResults = sourceResults.toList(),
                      resolveErrorMessage = ::resolveErrorMessage,
                      resolveWarningMessage = ::resolveProviderWarningMessage,
                    )
                    val anySuccess = sourceResults.any { it.result.isSuccess }
                    updateProject(entry.path) { project ->
                      project.copy(
                        threads = partial.threads,
                        providerWarnings = partial.providerWarnings,
                        isLoading = if (anySuccess) false else project.isLoading,
                      )
                    }
                  }
                }
              }
              // All sources done — final update with error/warning consolidation
              val finalResult = mergeAgentSessionSourceLoadResults(
                sourceResults = sourceResults.toList(),
                resolveErrorMessage = ::resolveErrorMessage,
                resolveWarningMessage = ::resolveProviderWarningMessage,
              )
              updateProject(entry.path) { project ->
                project.copy(isLoading = false,
                             hasLoaded = true,
                             hasUnknownThreadCount = finalResult.hasUnknownThreadCount,
                             threads = finalResult.threads,
                             errorMessage = finalResult.errorMessage,
                             providerWarnings = finalResult.providerWarnings)
              }
            }
            for (wt in entry.worktreeEntries) {
              launch {
                if (wt.project == null) {
                  updateWorktree(entry.path, wt.path) { it.copy(isLoading = false) }
                  return@launch
                }
                val sourceResults = java.util.concurrent.CopyOnWriteArrayList<AgentSessionSourceLoadResult>()
                coroutineScope {
                  for (source in sessionSources) {
                    launch {
                      val sourceResult = try {
                        val prefetched = prefetchedByProvider[source.provider]?.get(wt.path)
                        val threads = prefetched
                                      ?: source.listThreadsFromOpenProject(path = wt.path, project = wt.project)
                        AgentSessionSourceLoadResult(
                          provider = source.provider,
                          result = Result.success(threads),
                          hasUnknownTotal = !source.canReportExactThreadCount,
                        )
                      }
                      catch (e: Throwable) {
                        if (e is CancellationException) throw e
                        LOG.warn("Failed to load ${source.provider.name} sessions for ${wt.path}", e)
                        AgentSessionSourceLoadResult(
                          provider = source.provider,
                          result = Result.failure(e),
                        )
                      }
                      sourceResults.add(sourceResult)
                      val partial = mergeAgentSessionSourceLoadResults(
                        sourceResults = sourceResults.toList(),
                        resolveErrorMessage = ::resolveErrorMessage,
                        resolveWarningMessage = ::resolveProviderWarningMessage,
                      )
                      val anySuccess = sourceResults.any { it.result.isSuccess }
                      updateWorktree(entry.path, wt.path) { worktree ->
                        worktree.copy(
                          threads = partial.threads,
                          providerWarnings = partial.providerWarnings,
                          isLoading = if (anySuccess) false else worktree.isLoading,
                        )
                      }
                    }
                  }
                }
                val finalResult = mergeAgentSessionSourceLoadResults(
                  sourceResults = sourceResults.toList(),
                  resolveErrorMessage = ::resolveErrorMessage,
                  resolveWarningMessage = ::resolveProviderWarningMessage,
                )
                updateWorktree(entry.path, wt.path) { worktree ->
                  worktree.copy(isLoading = false,
                                hasLoaded = true,
                                hasUnknownThreadCount = finalResult.hasUnknownThreadCount,
                                threads = finalResult.threads,
                                errorMessage = finalResult.errorMessage,
                                providerWarnings = finalResult.providerWarnings)
                }
              }
            }
          }
        }
        mutableState.update { it.copy(lastUpdatedAt = System.currentTimeMillis()) }
      }
      catch (e: Throwable) {
        if (e is CancellationException) throw e
        LOG.error("Failed to load agent sessions", e)
        mutableState.update {
          it.copy(
            projects = it.projects.map { project ->
              project.copy(
                isLoading = false,
                hasLoaded = true,
                hasUnknownThreadCount = false,
                errorMessage = AgentSessionsBundle.message("toolwindow.error"),
                providerWarnings = emptyList(),
                worktrees = project.worktrees.map { wt ->
                  wt.copy(isLoading = false, hasUnknownThreadCount = false, providerWarnings = emptyList())
                },
              )
            },
            lastUpdatedAt = System.currentTimeMillis(),
          )
        }
      }
      finally {
        refreshMutex.unlock()
      }
    }
  }

  fun openOrFocusProject(path: String) {
    val normalized = normalizePath(path)
    val key = buildOpenProjectActionKey(normalized)
    actionGate.launch(
      scope = serviceScope,
      key = key,
      policy = SingleFlightPolicy.DROP,
      onDrop = { LOG.debug("Dropped duplicate open project action for $normalized") },
    ) {
      openOrFocusProjectInternal(path)
    }
  }

  fun showMoreProjects() {
    mutableState.update { it.copy(visibleProjectCount = it.visibleProjectCount + DEFAULT_VISIBLE_PROJECT_COUNT) }
  }

  fun showMoreThreads(path: String) {
    mutableState.update { state ->
      val current = state.visibleThreadCounts[path] ?: DEFAULT_VISIBLE_THREAD_COUNT
      state.copy(visibleThreadCounts = state.visibleThreadCounts + (path to (current + DEFAULT_VISIBLE_THREAD_COUNT)))
    }
  }

  fun ensureThreadVisible(path: String, provider: AgentSessionProvider, threadId: String) {
    val normalizedPath = normalizePath(path)
    mutableState.update { state ->
      val threadIndex = findThreadIndex(
        projects = state.projects,
        normalizedPath = normalizedPath,
        provider = provider,
        threadId = threadId,
      ) ?: return@update state
      val currentVisible = state.visibleThreadCounts[normalizedPath] ?: DEFAULT_VISIBLE_THREAD_COUNT
      if (threadIndex < currentVisible) {
        return@update state
      }
      val minVisible = threadIndex + 1
      var nextVisible = currentVisible
      while (nextVisible < minVisible) {
        nextVisible += DEFAULT_VISIBLE_THREAD_COUNT
      }
      state.copy(visibleThreadCounts = state.visibleThreadCounts + (normalizedPath to nextVisible))
    }
  }

  fun openChatThread(path: String, thread: AgentSessionThread, currentProject: Project? = null) {
    val normalized = normalizePath(path)
    val key = buildOpenThreadActionKey(path = normalized, thread = thread)
    actionGate.launch(
      scope = serviceScope,
      key = key,
      policy = SingleFlightPolicy.DROP,
      progress = dedicatedFrameOpenProgressRequest(currentProject),
      onDrop = {
        LOG.debug("Dropped duplicate open thread action for $normalized:${thread.provider}:${thread.id}")
      },
    ) {
      val worktreeBranch = findWorktreeBranch(normalized)
      val originBranch = thread.originBranch
      if (worktreeBranch != null && originBranch != null && originBranch != worktreeBranch && !isBranchMismatchDialogSuppressed()) {
        val proceed = withContext(Dispatchers.EDT) {
          showBranchMismatchDialog(originBranch, worktreeBranch)
        }
        if (!proceed) return@launch
      }
      openChat(path, thread, subAgent = null)
    }
  }

  fun openChatSubAgent(path: String, thread: AgentSessionThread, subAgent: AgentSubAgent, currentProject: Project? = null) {
    val normalized = normalizePath(path)
    val key = buildOpenSubAgentActionKey(path = normalized, thread = thread, subAgent = subAgent)
    actionGate.launch(
      scope = serviceScope,
      key = key,
      policy = SingleFlightPolicy.DROP,
      progress = dedicatedFrameOpenProgressRequest(currentProject),
      onDrop = {
        LOG.debug("Dropped duplicate open sub-agent action for $normalized:${thread.provider}:${thread.id}:${subAgent.id}")
      },
    ) {
      openChat(path, thread, subAgent)
    }
  }

  fun createNewSession(
    path: String,
    provider: AgentSessionProvider,
    yolo: Boolean = false,
    currentProject: Project? = null,
  ) {
    val normalized = normalizePath(path)
    val key = buildCreateSessionActionKey(normalized, provider, yolo)
    actionGate.launch(
      scope = serviceScope,
      key = key,
      policy = SingleFlightPolicy.DROP,
      progress = dedicatedFrameOpenProgressRequest(currentProject),
      onDrop = {
        LOG.debug("Dropped duplicate create session action for $normalized:$provider:yolo=$yolo")
      },
    ) {
      service<AgentSessionsTreeUiStateService>().setLastUsedProvider(provider)

      val identity: String
      val command: List<String>
      when (provider) {
        AgentSessionProvider.CLAUDE -> {
          command = buildAgentSessionNewCommand(provider, yolo)
          identity = buildAgentSessionNewIdentity(provider)
        }
        AgentSessionProvider.CODEX -> {
          val codexService = service<SharedCodexAppServerService>()
          val thread = codexService.createThread(cwd = normalized, yolo = yolo)
          command = CodexCliCommands.buildResumeCommand(thread.id)
          identity = buildAgentSessionIdentity(provider, thread.id)
        }
      }

      openNewChat(normalized, identity, command)
    }
  }

  fun loadProjectThreadsOnDemand(path: String) {
    serviceScope.launch(Dispatchers.IO) {
      val normalized = normalizePath(path)
      if (!markOnDemandLoading(normalized)) return@launch
      try {
        updateProject(normalized) { project ->
          project.copy(
            isLoading = true,
            hasUnknownThreadCount = false,
            errorMessage = null,
            providerWarnings = emptyList(),
          )
        }
        val result = loadThreadsFromClosedProject(path = normalized)
        updateProject(normalized) { project ->
          project.copy(
            isLoading = false,
            hasLoaded = true,
            hasUnknownThreadCount = result.hasUnknownThreadCount,
            threads = result.threads,
            errorMessage = result.errorMessage,
            providerWarnings = result.providerWarnings,
          )
        }
      }
      finally {
        clearOnDemandLoading(normalized)
      }
    }
  }

  fun loadWorktreeThreadsOnDemand(projectPath: String, worktreePath: String) {
    serviceScope.launch(Dispatchers.IO) {
      val normalizedProject = normalizePath(projectPath)
      val normalizedWorktree = normalizePath(worktreePath)
      if (!markWorktreeOnDemandLoading(normalizedProject, normalizedWorktree)) return@launch
      try {
        updateWorktree(normalizedProject, normalizedWorktree) { worktree ->
          worktree.copy(
            isLoading = true,
            hasUnknownThreadCount = false,
            errorMessage = null,
            providerWarnings = emptyList(),
          )
        }
        val result = loadThreadsFromClosedProject(path = normalizedWorktree)
        updateWorktree(normalizedProject, normalizedWorktree) { worktree ->
          worktree.copy(
            isLoading = false,
            hasLoaded = true,
            hasUnknownThreadCount = result.hasUnknownThreadCount,
            threads = result.threads,
            errorMessage = result.errorMessage,
            providerWarnings = result.providerWarnings,
          )
        }
      }
      finally {
        clearWorktreeOnDemandLoading(normalizedWorktree)
      }
    }
  }

  private suspend fun loadThreadsFromClosedProject(path: String): AgentSessionLoadResult {
    return loadThreads(path) { source ->
      source.listThreadsFromClosedProject(path = path)
    }
  }

  private suspend fun loadThreads(
    path: String,
    loadOperation: suspend (AgentSessionSource) -> List<AgentSessionThread>,
  ): AgentSessionLoadResult {
    val sourceResults = coroutineScope {
      sessionSources.map { source ->
        async {
          val result = try {
            Result.success(loadOperation(source))
          }
          catch (throwable: Throwable) {
            if (throwable is CancellationException) throw throwable
            LOG.warn("Failed to load ${source.provider.name} sessions for $path", throwable)
            Result.failure(throwable)
          }
          AgentSessionSourceLoadResult(
            provider = source.provider,
            result = result,
            hasUnknownTotal = result.isSuccess && !source.canReportExactThreadCount,
          )
        }
      }.awaitAll()
    }
    return mergeAgentSessionSourceLoadResults(
      sourceResults = sourceResults,
      resolveErrorMessage = ::resolveErrorMessage,
      resolveWarningMessage = ::resolveProviderWarningMessage,
    )
  }

  private fun resolveErrorMessage(provider: AgentSessionProvider, t: Throwable): @NlsContexts.DialogMessage String {
    return when (t) {
      is CodexCliNotFoundException -> resolveCliMissingMessage(provider)
      else -> AgentSessionsBundle.message("toolwindow.error")
    }
  }

  private fun resolveCliMissingMessage(provider: AgentSessionProvider): @NlsContexts.DialogMessage String {
    return AgentSessionsBundle.message(agentSessionCliMissingMessageKey(provider))
  }

  private fun resolveProviderWarningMessage(provider: AgentSessionProvider, t: Throwable): @NlsContexts.DialogMessage String {
    return when (t) {
      is CodexCliNotFoundException -> resolveCliMissingMessage(provider)
      else -> AgentSessionsBundle.message("toolwindow.warning.provider.unavailable", resolveProviderLabel(provider))
    }
  }

  private fun resolveProviderLabel(provider: AgentSessionProvider): String {
    return when (provider) {
      AgentSessionProvider.CODEX -> AgentSessionsBundle.message("toolwindow.provider.codex")
      AgentSessionProvider.CLAUDE -> AgentSessionsBundle.message("toolwindow.provider.claude")
    }
  }

  private suspend fun openOrFocusProjectInternal(path: String) {
    val normalized = normalizePath(path)
    val openProject = findOpenProject(normalized)
    if (openProject != null) {
      withContext(Dispatchers.EDT) {
        ProjectUtilService.getInstance(openProject).focusProjectWindow()
      }
      return
    }
    val manager = RecentProjectsManager.getInstance() as? RecentProjectsManagerBase ?: return
    val projectPath = try {
      Path.of(path)
    }
    catch (_: InvalidPathException) {
      return
    }
    manager.openProject(projectFile = projectPath, options = OpenProjectTask())
  }

  private suspend fun openChat(
    path: String,
    thread: AgentSessionThread,
    subAgent: AgentSubAgent?,
    shellCommandOverride: List<String>? = null,
  ) {
    val normalized = normalizePath(path)
    if (AgentChatOpenModeSettings.openInDedicatedFrame()) {
      openChatInDedicatedFrame(normalized, thread, subAgent, shellCommandOverride)
      return
    }
    val openProject = findOpenProject(normalized)
    if (openProject != null) {
      openChatInProject(openProject, normalized, thread, subAgent, shellCommandOverride)
      return
    }
    val manager = RecentProjectsManager.getInstance() as? RecentProjectsManagerBase ?: return
    val projectPath = try {
      Path.of(path)
    }
    catch (_: InvalidPathException) {
      return
    }
    val connection = ApplicationManager.getApplication().messageBus.connect(serviceScope)
    connection.subscribe(ProjectManager.TOPIC, object : ProjectManagerListener {
      @Deprecated("Deprecated in Java")
      @Suppress("removal")
      override fun projectOpened(project: Project) {
        if (resolveProjectPath(manager, project) != normalized) return
        serviceScope.launch {
          openChatInProject(project, normalized, thread, subAgent, shellCommandOverride)
          connection.disconnect()
        }
      }
    })
    manager.openProject(projectFile = projectPath, options = OpenProjectTask())
  }

  private fun buildOpenProjectActionKey(path: String): String {
    return "$OPEN_PROJECT_ACTION_KEY_PREFIX:$path"
  }

  private fun buildCreateSessionActionKey(path: String, provider: AgentSessionProvider, yolo: Boolean): String {
    return "$CREATE_SESSION_ACTION_KEY_PREFIX:$path:$provider:yolo=$yolo"
  }

  private fun buildOpenThreadActionKey(path: String, thread: AgentSessionThread): String {
    return "$OPEN_THREAD_ACTION_KEY_PREFIX:$path:${thread.provider}:${thread.id}"
  }

  private fun buildOpenSubAgentActionKey(path: String, thread: AgentSessionThread, subAgent: AgentSubAgent): String {
    return "$OPEN_SUB_AGENT_ACTION_KEY_PREFIX:$path:${thread.provider}:${thread.id}:${subAgent.id}"
  }

  private fun dedicatedFrameOpenProgressRequest(currentProject: Project?): SingleFlightProgressRequest? {
    if (!AgentChatOpenModeSettings.openInDedicatedFrame()) return null
    return SingleFlightProgressRequest(
      project = currentOrDefaultProject(currentProject),
      title = AgentSessionsBundle.message("toolwindow.progress.opening.dedicated.frame"),
    )
  }

  private suspend fun openNewChat(path: String, identity: String, command: List<String>) {
    val title = AgentSessionsBundle.message("toolwindow.action.new.thread")
    val dedicatedFrame = AgentChatOpenModeSettings.openInDedicatedFrame()
    if (dedicatedFrame) {
      openNewChatInDedicatedFrame(path, identity, command, title)
      return
    }
    val openProject = findOpenProject(path)
    if (openProject != null) {
      openNewChatInProject(openProject, path, identity, command, title)
      return
    }
    val manager = RecentProjectsManager.getInstance() as? RecentProjectsManagerBase ?: return
    val projectPath = try {
      Path.of(path)
    }
    catch (_: InvalidPathException) {
      return
    }
    val connection = ApplicationManager.getApplication().messageBus.connect(serviceScope)
    connection.subscribe(ProjectManager.TOPIC, object : ProjectManagerListener {
      @Deprecated("Deprecated in Java")
      @Suppress("removal")
      override fun projectOpened(project: Project) {
        if (resolveProjectPath(manager, project) != path) return
        serviceScope.launch {
          openNewChatInProject(project, path, identity, command, title)
          connection.disconnect()
        }
      }
    })
    manager.openProject(projectFile = projectPath, options = OpenProjectTask())
  }

  private suspend fun openNewChatInDedicatedFrame(
    path: String,
    identity: String,
    command: List<String>,
    title: String,
  ) {
    val dedicatedProjectPath = AgentWorkbenchDedicatedFrameProjectManager.dedicatedProjectPath()
    val openProject = findOpenProject(dedicatedProjectPath)
    if (openProject != null) {
      AgentWorkbenchDedicatedFrameProjectManager.configureProject(openProject)
      openNewChatInProject(openProject, path, identity, command, title)
      return
    }

    val manager = RecentProjectsManager.getInstance() as? RecentProjectsManagerBase ?: return
    val dedicatedProjectDir = try {
      AgentWorkbenchDedicatedFrameProjectManager.ensureProjectPath()
    }
    catch (e: Throwable) {
      LOG.warn("Failed to prepare dedicated chat frame project", e)
      return
    }

    val connection = ApplicationManager.getApplication().messageBus.connect(serviceScope)
    connection.subscribe(ProjectManager.TOPIC, object : ProjectManagerListener {
      @Deprecated("Deprecated in Java")
      @Suppress("removal")
      override fun projectOpened(project: Project) {
        val projectPath2 = resolveProjectPath(manager, project)
        if (projectPath2 != dedicatedProjectPath) return
        serviceScope.launch {
          AgentWorkbenchDedicatedFrameProjectManager.configureProject(project)
          openNewChatInProject(project, path, identity, command, title)
          connection.disconnect()
        }
      }
    })
    try {
      val result = manager.openProject(projectFile = dedicatedProjectDir, options = OpenProjectTask(forceOpenInNewFrame = true))
      if (result == null) {
        connection.disconnect()
      }
    }
    catch (e: Throwable) {
      connection.disconnect()
      if (e is CancellationException) throw e
    }
  }

  private suspend fun openNewChatInProject(
    project: Project,
    projectPath: String,
    identity: String,
    command: List<String>,
    title: String,
  ) {
    withContext(Dispatchers.EDT) {
      project.service<AgentChatEditorService>().openChat(
        projectPath = projectPath,
        threadIdentity = identity,
        shellCommand = command,
        threadId = identity,
        threadTitle = title,
        subAgentId = null,
      )
      ProjectUtilService.getInstance(project).focusProjectWindow()
    }
  }

  private suspend fun openChatInDedicatedFrame(
    path: String,
    thread: AgentSessionThread,
    subAgent: AgentSubAgent?,
    shellCommandOverride: List<String>?,
  ) {
    val dedicatedProjectPath = AgentWorkbenchDedicatedFrameProjectManager.dedicatedProjectPath()
    val openProject = findOpenProject(dedicatedProjectPath)
    if (openProject != null) {
      AgentWorkbenchDedicatedFrameProjectManager.configureProject(openProject)
      openChatInProject(openProject, path, thread, subAgent, shellCommandOverride)
      return
    }

    val manager = RecentProjectsManager.getInstance() as? RecentProjectsManagerBase ?: return
    val dedicatedProjectDir = try {
      AgentWorkbenchDedicatedFrameProjectManager.ensureProjectPath()
    }
    catch (e: Throwable) {
      LOG.warn("Failed to prepare dedicated chat frame project", e)
      return
    }

    val connection = ApplicationManager.getApplication().messageBus.connect(serviceScope)
    connection.subscribe(ProjectManager.TOPIC, object : ProjectManagerListener {
      @Deprecated("Deprecated in Java")
      @Suppress("removal")
      override fun projectOpened(project: Project) {
        val projectPath2 = resolveProjectPath(manager, project)
        if (projectPath2 != dedicatedProjectPath) return
        serviceScope.launch {
          AgentWorkbenchDedicatedFrameProjectManager.configureProject(project)
          openChatInProject(project, path, thread, subAgent, shellCommandOverride)
          connection.disconnect()
        }
      }
    })
    try {
      val result = manager.openProject(projectFile = dedicatedProjectDir, options = OpenProjectTask(forceOpenInNewFrame = true))
      if (result == null) {
        connection.disconnect()
      }
    }
    catch (e: Throwable) {
      connection.disconnect()
      if (e is CancellationException) throw e
    }
  }

  private suspend fun openChatInProject(
    project: Project,
    projectPath: String,
    thread: AgentSessionThread,
    subAgent: AgentSubAgent?,
    shellCommandOverride: List<String>?,
  ) {
    val identity = buildAgentSessionIdentity(provider = thread.provider, sessionId = thread.id)
    val command = buildAgentSessionResumeCommand(provider = thread.provider, sessionId = thread.id)
    withContext(Dispatchers.EDT) {
      project.service<AgentChatEditorService>().openChat(
        projectPath = projectPath,
        threadIdentity = identity,
        shellCommand = shellCommandOverride ?: command,
        threadId = thread.id,
        threadTitle = thread.title,
        subAgentId = subAgent?.id,
      )
      ProjectUtilService.getInstance(project).focusProjectWindow()
    }
  }

  private fun updateProject(path: String, update: (AgentProjectSessions) -> AgentProjectSessions) {
    mutableState.update { state ->
      val next = state.projects.map { project ->
        if (project.path == path) update(project) else project
      }
      state.copy(projects = next, lastUpdatedAt = System.currentTimeMillis())
    }
  }

  private fun updateWorktree(projectPath: String, worktreePath: String, update: (AgentWorktree) -> AgentWorktree) {
    mutableState.update { state ->
      val next = state.projects.map { project ->
        if (project.path == projectPath) {
          project.copy(worktrees = project.worktrees.map { wt ->
            if (wt.path == worktreePath) update(wt) else wt
          })
        }
        else project
      }
      state.copy(projects = next, lastUpdatedAt = System.currentTimeMillis())
    }
  }

  private suspend fun markOnDemandLoading(path: String): Boolean {
    return onDemandMutex.withLock {
      val project = state.value.projects.firstOrNull { it.path == path } ?: return@withLock false
      if (project.isOpen || project.isLoading || project.hasLoaded) return@withLock false
      if (!onDemandLoading.add(path)) return@withLock false
      true
    }
  }

  private suspend fun clearOnDemandLoading(path: String) {
    onDemandMutex.withLock {
      onDemandLoading.remove(path)
    }
  }

  private suspend fun markWorktreeOnDemandLoading(projectPath: String, worktreePath: String): Boolean {
    return onDemandMutex.withLock {
      val project = state.value.projects.firstOrNull { it.path == projectPath } ?: return@withLock false
      val worktree = project.worktrees.firstOrNull { it.path == worktreePath } ?: return@withLock false
      if (worktree.isLoading || worktree.hasLoaded) return@withLock false
      if (!onDemandWorktreeLoading.add(worktreePath)) return@withLock false
      true
    }
  }

  private suspend fun clearWorktreeOnDemandLoading(worktreePath: String) {
    onDemandMutex.withLock {
      onDemandWorktreeLoading.remove(worktreePath)
    }
  }

  private suspend fun collectProjects(): List<ProjectEntry> {
    val rawEntries = collectRawProjectEntries()
    if (rawEntries.isEmpty()) return emptyList()

    val repoRootByPath = rawEntries.associate { entry ->
      entry.path to GitWorktreeDiscovery.detectRepoRoot(entry.path)
    }

    data class RepoGroup(
      val repoRoot: String,
      val members: MutableList<IndexedValue<ProjectEntry>>,
    )

    val repoGroups = LinkedHashMap<String, RepoGroup>()
    val standaloneEntries = mutableListOf<IndexedValue<ProjectEntry>>()

    rawEntries.forEachIndexed { index, entry ->
      val repoRoot = repoRootByPath[entry.path]
      if (repoRoot != null) {
        val group = repoGroups.getOrPut(repoRoot) {
          RepoGroup(repoRoot, mutableListOf())
        }
        group.members.add(IndexedValue(index, entry))
      }
      else {
        standaloneEntries.add(IndexedValue(index, entry))
      }
    }

    // Discover all worktrees in parallel across repo roots (main + linked).
    val discoveredByRepoRoot = coroutineScope {
      repoGroups.keys.map { repoRoot ->
        async { repoRoot to GitWorktreeDiscovery.discoverWorktrees(repoRoot) }
      }.awaitAll().toMap()
    }

    val resultEntries = mutableListOf<IndexedValue<ProjectEntry>>()
    for ((repoRoot, group) in repoGroups) {
      val mainRaw = group.members.firstOrNull { it.value.path == repoRoot }
      val worktreeRaws = group.members.filter { it.value.path != repoRoot }
      val firstIndex = group.members.minOf { it.index }

      val discoveredWorktrees = discoveredByRepoRoot[repoRoot] ?: emptyList()
      val worktreeEntries = buildWorktreeEntries(worktreeRaws.map { it.value }, discoveredWorktrees)
      val mainBranch = shortBranchName(discoveredWorktrees.firstOrNull { it.isMain }?.branch)

      if (worktreeEntries.isEmpty()) {
        val raw = mainRaw?.value ?: continue
        resultEntries.add(IndexedValue(firstIndex, raw))
      }
      else {
        val entry = mainRaw?.value?.copy(worktreeEntries = worktreeEntries, branch = mainBranch)
                    ?: ProjectEntry(
                      path = repoRoot,
                      name = worktreeDisplayName(repoRoot),
                      project = null,
                      branch = mainBranch,
                      worktreeEntries = worktreeEntries,
                    )
        resultEntries.add(IndexedValue(firstIndex, entry))
      }
    }

    for (indexed in standaloneEntries) {
      resultEntries.add(indexed)
    }

    return resultEntries.sortedBy { it.index }.map { it.value }
  }

  private fun buildWorktreeEntries(
    openRawEntries: List<ProjectEntry>,
    discovered: List<GitWorktreeInfo>,
  ): List<WorktreeEntry> {
    val openPaths = openRawEntries.mapTo(LinkedHashSet()) { it.path }
    val result = mutableListOf<WorktreeEntry>()

    for (raw in openRawEntries) {
      val gitInfo = discovered.firstOrNull { it.path == raw.path }
      result.add(WorktreeEntry(
        path = raw.path,
        name = raw.name,
        branch = shortBranchName(gitInfo?.branch),
        project = raw.project,
      ))
    }

    for (info in discovered) {
      if (info.path !in openPaths && !info.isMain) {
        result.add(WorktreeEntry(
          path = info.path,
          name = worktreeDisplayName(info.path),
          branch = shortBranchName(info.branch),
          project = null,
        ))
      }
    }

    return result
  }

  private fun collectRawProjectEntries(): List<ProjectEntry> {
    val manager = RecentProjectsManager.getInstance() as? RecentProjectsManagerBase
                  ?: return emptyList()
    val dedicatedProjectPath = AgentWorkbenchDedicatedFrameProjectManager.dedicatedProjectPath()
    val openProjects = ProjectManager.getInstance().openProjects
    val openByPath = LinkedHashMap<String, Project>()
    for (project in openProjects) {
      val path = manager.getProjectPath(project)?.invariantSeparatorsPathString
                 ?: project.basePath?.let(::normalizePath)
                 ?: continue
      if (path == dedicatedProjectPath || AgentWorkbenchDedicatedFrameProjectManager.isDedicatedProjectPath(path)) continue
      openByPath[path] = project
    }
    val seen = LinkedHashSet<String>()
    val entries = mutableListOf<ProjectEntry>()
    for (path in manager.getRecentPaths()) {
      val normalized = normalizePath(path)
      if (normalized == dedicatedProjectPath || AgentWorkbenchDedicatedFrameProjectManager.isDedicatedProjectPath(normalized)) continue
      if (!seen.add(normalized)) continue
      entries.add(
        ProjectEntry(
          path = normalized,
          name = resolveProjectName(manager, normalized, openByPath[normalized]),
          project = openByPath[normalized],
        )
      )
    }
    for ((path, project) in openByPath) {
      if (!seen.add(path)) continue
      entries.add(
        ProjectEntry(
          path = path,
          name = resolveProjectName(manager, path, project),
          project = project,
        )
      )
    }
    return entries
  }

  private fun resolveProjectName(
    manager: RecentProjectsManagerBase,
    path: String,
    project: Project?,
  ): String {
    val displayName = manager.getDisplayName(path).takeIf { !it.isNullOrBlank() }
    if (displayName != null) return displayName
    val projectName = manager.getProjectName(path)
    if (projectName.isNotBlank()) return projectName
    if (project != null) return project.name
    return resolveProjectNameWithoutManager(path, project)
  }

  private fun resolveProjectNameWithoutManager(path: String, project: Project?): String {
    if (project != null) return project.name
    val fileName = try {
      Path.of(path).name
    }
    catch (_: InvalidPathException) {
      null
    }
    return fileName ?: FileUtilRt.toSystemDependentName(path)
  }

  private fun findOpenProject(path: String): Project? {
    val manager = RecentProjectsManager.getInstance() as? RecentProjectsManagerBase ?: return null
    val normalized = normalizePath(path)
    return ProjectManager.getInstance().openProjects.firstOrNull { project ->
      resolveProjectPath(manager, project) == normalized
    }
  }

  private fun resolveProjectPath(manager: RecentProjectsManagerBase, project: Project): String? {
    return manager.getProjectPath(project)?.invariantSeparatorsPathString
           ?: project.basePath?.let(::normalizePath)
  }

  private fun findWorktreeBranch(path: String): String? {
    for (project in state.value.projects) {
      for (worktree in project.worktrees) {
        if (worktree.path == path) return worktree.branch
      }
    }
    return null
  }

  private fun isBranchMismatchDialogSuppressed(): Boolean {
    return PropertiesComponent.getInstance().getBoolean(SUPPRESS_BRANCH_MISMATCH_DIALOG_KEY, false)
  }

  private fun showBranchMismatchDialog(originBranch: String, currentBranch: String): Boolean {
    return MessageDialogBuilder
      .okCancel(
        AgentSessionsBundle.message("toolwindow.thread.branch.mismatch.dialog.title"),
        AgentSessionsBundle.message("toolwindow.thread.branch.mismatch.dialog.message", originBranch, currentBranch),
      )
      .yesText(AgentSessionsBundle.message("toolwindow.thread.branch.mismatch.dialog.continue"))
      .doNotAsk(object : DoNotAskOption.Adapter() {
        override fun rememberChoice(isSelected: Boolean, exitCode: Int) {
          if (isSelected) {
            PropertiesComponent.getInstance().setValue(SUPPRESS_BRANCH_MISMATCH_DIALOG_KEY, true)
          }
        }
      })
      .asWarning()
      .ask(null as Project?)
  }

  private fun normalizePath(path: String): String {
    return try {
      Path.of(path).invariantSeparatorsPathString
    }
    catch (_: InvalidPathException) {
      path
    }
  }

  private fun findThreadIndex(
    projects: List<AgentProjectSessions>,
    normalizedPath: String,
    provider: AgentSessionProvider,
    threadId: String,
  ): Int? {
    val projectThreads = projects.firstOrNull { it.path == normalizedPath }?.threads
    if (projectThreads != null) {
      val index = projectThreads.indexOfFirst { it.provider == provider && it.id == threadId }
      if (index >= 0) return index
    }

    projects.forEach { project ->
      val worktreeThreads = project.worktrees.firstOrNull { it.path == normalizedPath }?.threads ?: return@forEach
      val index = worktreeThreads.indexOfFirst { it.provider == provider && it.id == threadId }
      if (index >= 0) return index
    }

    return null
  }

  internal data class ProjectEntry(
    val path: String,
    val name: String,
    val project: Project?,
    val branch: String? = null,
    val worktreeEntries: List<WorktreeEntry> = emptyList(),
  )

  internal data class WorktreeEntry(
    val path: String,
    val name: String,
    val branch: String?,
    val project: Project?,
  )
}

internal data class AgentSessionLoadResult(
  val threads: List<AgentSessionThread>,
  val errorMessage: String? = null,
  val hasUnknownThreadCount: Boolean = false,
  val providerWarnings: List<AgentSessionProviderWarning> = emptyList(),
)

internal data class AgentSessionSourceLoadResult(
  val provider: AgentSessionProvider,
  val result: Result<List<AgentSessionThread>>,
  val hasUnknownTotal: Boolean = false,
)

internal fun mergeAgentSessionSourceLoadResults(
  sourceResults: List<AgentSessionSourceLoadResult>,
  resolveErrorMessage: (AgentSessionProvider, Throwable) -> String,
  resolveWarningMessage: (AgentSessionProvider, Throwable) -> String = resolveErrorMessage,
): AgentSessionLoadResult {
  val mergedThreads = buildList {
    sourceResults.forEach { sourceResult ->
      addAll(sourceResult.result.getOrElse { emptyList() })
    }
  }.sortedByDescending { it.updatedAt }

  val providerWarnings = sourceResults.mapNotNull { sourceResult ->
    sourceResult.result.exceptionOrNull()?.let { throwable ->
      AgentSessionProviderWarning(
        provider = sourceResult.provider,
        message = resolveWarningMessage(sourceResult.provider, throwable),
      )
    }
  }
  val hasUnknownThreadCount = sourceResults.any { it.hasUnknownTotal }

  val firstError = sourceResults.firstNotNullOfOrNull { sourceResult ->
    sourceResult.result.exceptionOrNull()?.let { throwable ->
      resolveErrorMessage(sourceResult.provider, throwable)
    }
  }
  val allSourcesFailed = sourceResults.isNotEmpty() && sourceResults.all { it.result.isFailure }
  val errorMessage = if (allSourcesFailed) firstError else null
  return AgentSessionLoadResult(
    threads = mergedThreads,
    errorMessage = errorMessage,
    hasUnknownThreadCount = hasUnknownThreadCount,
    providerWarnings = if (allSourcesFailed) emptyList() else providerWarnings,
  )
}
