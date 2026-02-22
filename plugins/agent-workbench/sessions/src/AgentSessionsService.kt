// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

// @spec community/plugins/agent-workbench/spec/agent-sessions.spec.md
// @spec community/plugins/agent-workbench/spec/agent-dedicated-frame.spec.md
// @spec community/plugins/agent-workbench/spec/actions/new-thread.spec.md

import com.intellij.agent.workbench.chat.AgentChatTabSelectionService
import com.intellij.agent.workbench.chat.closeAndForgetAgentChatsForThread
import com.intellij.agent.workbench.chat.openChat
import com.intellij.agent.workbench.common.AgentThreadActivity
import com.intellij.agent.workbench.common.normalizeAgentWorkbenchPath
import com.intellij.agent.workbench.sessions.providers.AgentSessionProviderBridges
import com.intellij.agent.workbench.sessions.providers.AgentSessionSource
import com.intellij.ide.RecentProjectsManager
import com.intellij.ide.RecentProjectsManagerBase
import com.intellij.ide.impl.OpenProjectTask
import com.intellij.ide.impl.ProjectUtilService
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.UI
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.openapi.project.currentOrDefaultProject
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.ui.DoNotAskOption
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.util.messages.SimpleMessageBusConnection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.file.InvalidPathException
import java.nio.file.Path
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.time.Duration.Companion.seconds

private val LOG = logger<AgentSessionsService>()

private const val SUPPRESS_BRANCH_MISMATCH_DIALOG_KEY = "agent.workbench.suppress.branch.mismatch.dialog"
private const val OPEN_PROJECT_ACTION_KEY_PREFIX = "project-open"
private const val CREATE_SESSION_ACTION_KEY_PREFIX = "session-create"
private const val OPEN_THREAD_ACTION_KEY_PREFIX = "thread-open"
private const val OPEN_SUB_AGENT_ACTION_KEY_PREFIX = "subagent-open"
private const val ARCHIVE_THREAD_ACTION_KEY_PREFIX = "thread-archive"
private val CODEX_ARCHIVE_REFRESH_DELAY = 1.seconds

@Service(Service.Level.APP)
internal class AgentSessionsService private constructor(
  private val serviceScope: CoroutineScope,
  private val sessionSourcesProvider: () -> List<AgentSessionSource>,
  private val projectEntriesProvider: suspend (AgentSessionsService) -> List<ProjectEntry>,
  private val treeUiState: SessionsTreeUiState,
  private val archiveChatCleanup: suspend (projectPath: String, threadIdentity: String) -> Unit,
  subscribeToProjectLifecycle: Boolean,
) {
  @Suppress("unused")
  constructor(serviceScope: CoroutineScope) : this(
    serviceScope = serviceScope,
    sessionSourcesProvider = AgentSessionProviderBridges::sessionSources,
    projectEntriesProvider = { service -> service.projectCatalog.collectProjects() },
    treeUiState = service<AgentSessionsTreeUiStateService>(),
    archiveChatCleanup = { projectPath, threadIdentity ->
      closeAndForgetAgentChatsForThread(projectPath = projectPath, threadIdentity = threadIdentity)
    },
    subscribeToProjectLifecycle = true,
  )

  internal constructor(
    serviceScope: CoroutineScope,
    sessionSourcesProvider: () -> List<AgentSessionSource>,
    projectEntriesProvider: suspend () -> List<ProjectEntry>,
    treeUiState: SessionsTreeUiState = InMemorySessionsTreeUiState(),
    archiveChatCleanup: suspend (projectPath: String, threadIdentity: String) -> Unit = { _, _ -> },
    subscribeToProjectLifecycle: Boolean = false,
  ) : this(
    serviceScope = serviceScope,
    sessionSourcesProvider = sessionSourcesProvider,
    projectEntriesProvider = { _ -> projectEntriesProvider() },
    treeUiState = treeUiState,
    archiveChatCleanup = archiveChatCleanup,
    subscribeToProjectLifecycle = subscribeToProjectLifecycle,
  )

  private val actionGate = SingleFlightActionGate()
  internal val projectCatalog = AgentSessionsProjectCatalog()
  private val stateStore = AgentSessionsStateStore(treeUiState)
  private val loadingCoordinator = AgentSessionsLoadingCoordinator(
    serviceScope = serviceScope,
    sessionSourcesProvider = sessionSourcesProvider,
    projectEntriesProvider = { projectEntriesProvider(this@AgentSessionsService) },
    treeUiState = treeUiState,
    stateStore = stateStore,
    isRefreshGateActive = ::isSourceRefreshGateActive,
  )

  val state: StateFlow<AgentSessionsState> = stateStore.state

  init {
    loadingCoordinator.observeSessionSourceUpdates()

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

  private suspend fun isSourceRefreshGateActive(): Boolean = withContext(Dispatchers.EDT) {
    val stateSnapshot = stateStore.snapshot()
    val hasLoadedPaths = stateSnapshot.projects.any { project ->
      project.hasLoaded || project.worktrees.any { it.hasLoaded }
    }

    val openProjects = ProjectManager.getInstance().openProjects
    if (openProjects.isEmpty()) {
      val decision = stateSnapshot.projects.any { project ->
        project.isOpen || project.hasLoaded || project.worktrees.any { it.isOpen || it.hasLoaded }
      }
      LOG.debug {
        "Source refresh gate decision=$decision (openProjects=0, stateProjects=${stateSnapshot.projects.size}, hasLoadedPaths=$hasLoadedPaths)"
      }
      return@withContext decision
    }

    data class ProjectRefreshSignal(
      val name: String,
      val dedicated: Boolean,
      val sessionsVisible: Boolean,
      val chatActive: Boolean,
    )

    val signals = openProjects.map { project ->
      ProjectRefreshSignal(
        name = project.name,
        dedicated = AgentWorkbenchDedicatedFrameProjectManager.isDedicatedProject(project),
        sessionsVisible = isSessionsToolWindowVisible(project),
        chatActive = isAgentChatActive(project),
      )
    }

    val uiSignalActive = signals.any { signal ->
      signal.sessionsVisible || signal.chatActive
    }
    val decision = uiSignalActive || hasLoadedPaths

    LOG.debug {
      val signalText = signals.joinToString(separator = ";") { signal ->
        "${signal.name}[dedicated=${signal.dedicated},sessionsVisible=${signal.sessionsVisible},chatActive=${signal.chatActive}]"
      }
      "Source refresh gate decision=$decision (openProjects=${openProjects.size}, uiSignalActive=$uiSignalActive, hasLoadedPaths=$hasLoadedPaths, signals=$signalText)"
    }

    decision
  }

  private fun isSessionsToolWindowVisible(project: Project): Boolean {
    return ToolWindowManager.getInstance(project)
      .getToolWindow(AGENT_SESSIONS_TOOL_WINDOW_ID)
      ?.isVisible == true
  }

  private fun isAgentChatActive(project: Project): Boolean {
    return runCatching {
      val selectionService = project.service<AgentChatTabSelectionService>()
      selectionService.selectedChatTab.value != null || selectionService.hasOpenChatTabs()
    }.getOrDefault(false)
  }

  fun refresh() {
    loadingCoordinator.refresh()
  }

  fun openOrFocusProject(path: String) {
    val normalizedPath = normalizeAgentWorkbenchPath(path)
    launchDropAction(
      key = buildOpenProjectActionKey(normalizedPath),
      droppedActionMessage = "Dropped duplicate open project action for $normalizedPath",
    ) {
      openOrFocusProjectInternal(path)
    }
  }

  fun showMoreProjects() {
    stateStore.showMoreProjects()
  }

  fun showMoreThreads(path: String) {
    stateStore.showMoreThreads(path)
  }

  fun ensureThreadVisible(path: String, provider: AgentSessionProvider, threadId: String) {
    stateStore.ensureThreadVisible(path, provider, threadId)
  }

  fun canArchiveThread(thread: AgentSessionThread): Boolean {
    val bridge = AgentSessionProviderBridges.find(thread.provider) ?: return false
    return bridge.supportsArchiveThread
  }

  fun openChatThread(path: String, thread: AgentSessionThread, currentProject: Project? = null) {
    val normalizedPath = normalizeAgentWorkbenchPath(path)
    markClaudeQuotaHintEligible(thread.provider)
    launchDropAction(
      key = buildOpenThreadActionKey(path = normalizedPath, thread = thread),
      droppedActionMessage = "Dropped duplicate open thread action for $normalizedPath:${thread.provider}:${thread.id}",
      progress = dedicatedFrameOpenProgressRequest(currentProject),
    ) {
      val worktreeBranch = stateStore.findWorktreeBranch(normalizedPath)
      val originBranch = thread.originBranch
      if (worktreeBranch != null && originBranch != null && originBranch != worktreeBranch && !isBranchMismatchDialogSuppressed()) {
        val proceed = withContext(Dispatchers.EDT) {
          showBranchMismatchDialog(originBranch, worktreeBranch)
        }
        if (!proceed) return@launchDropAction
      }
      openChat(path = path, thread = thread, subAgent = null, serviceScope = serviceScope)
    }
  }

  fun openChatSubAgent(path: String, thread: AgentSessionThread, subAgent: AgentSubAgent, currentProject: Project? = null) {
    val normalizedPath = normalizeAgentWorkbenchPath(path)
    markClaudeQuotaHintEligible(thread.provider)
    launchDropAction(
      key = buildOpenSubAgentActionKey(path = normalizedPath, thread = thread, subAgent = subAgent),
      droppedActionMessage = "Dropped duplicate open sub-agent action for $normalizedPath:${thread.provider}:${thread.id}:${subAgent.id}",
      progress = dedicatedFrameOpenProgressRequest(currentProject),
    ) {
      openChat(path = path, thread = thread, subAgent = subAgent, serviceScope = serviceScope)
    }
  }

  fun createNewSession(
    path: String,
    provider: AgentSessionProvider,
    mode: AgentSessionLaunchMode = AgentSessionLaunchMode.STANDARD,
    currentProject: Project? = null,
  ) {
    val normalizedPath = normalizeAgentWorkbenchPath(path)
    markClaudeQuotaHintEligible(provider)
    launchDropAction(
      key = buildCreateSessionActionKey(normalizedPath, provider, mode),
      droppedActionMessage = "Dropped duplicate create session action for $normalizedPath:$provider:mode=$mode",
      progress = dedicatedFrameOpenProgressRequest(currentProject),
    ) {
      service<AgentSessionsTreeUiStateService>().setLastUsedProvider(provider)

      val bridge = AgentSessionProviderBridges.find(provider)
      if (bridge == null) {
        logMissingProviderBridge(provider)
        loadingCoordinator.appendProviderUnavailableWarning(normalizedPath, provider)
        return@launchDropAction
      }
      if (mode !in bridge.supportedLaunchModes) {
        LOG.warn("Session provider bridge ${provider.value} does not support launch mode $mode")
        loadingCoordinator.appendProviderUnavailableWarning(normalizedPath, provider)
        return@launchDropAction
      }

      val launchSpec = bridge.createNewSession(path = normalizedPath, mode = mode)
      val identity = launchSpec.sessionId?.let { sessionId ->
        buildAgentSessionIdentity(provider, sessionId)
      } ?: buildAgentSessionNewIdentity(provider)

      openNewChat(path = normalizedPath, identity = identity, command = launchSpec.command, serviceScope = serviceScope)
    }
  }

  fun archiveThread(path: String, thread: AgentSessionThread) {
    val normalizedPath = normalizeAgentWorkbenchPath(path)
    launchDropAction(
      key = buildArchiveThreadActionKey(path = normalizedPath, thread = thread),
      droppedActionMessage = "Dropped duplicate archive thread action for $normalizedPath:${thread.provider}:${thread.id}",
    ) {
      val bridge = AgentSessionProviderBridges.find(thread.provider)
      if (bridge == null) {
        logMissingProviderBridge(thread.provider)
        loadingCoordinator.appendProviderUnavailableWarning(normalizedPath, thread.provider)
        return@launchDropAction
      }
      if (!bridge.supportsArchiveThread) {
        LOG.warn("Session provider bridge ${thread.provider.value} does not support archive")
        loadingCoordinator.appendProviderUnavailableWarning(normalizedPath, thread.provider)
        return@launchDropAction
      }

      val archived = try {
        bridge.archiveThread(path = normalizedPath, threadId = thread.id)
      }
      catch (t: Throwable) {
        if (t is CancellationException) {
          throw t
        }
        LOG.warn("Failed to archive thread ${thread.provider}:${thread.id}", t)
        loadingCoordinator.appendProviderUnavailableWarning(normalizedPath, thread.provider)
        return@launchDropAction
      }

      if (!archived) {
        loadingCoordinator.appendProviderUnavailableWarning(normalizedPath, thread.provider)
        return@launchDropAction
      }

      if (thread.provider == AgentSessionProvider.CODEX) {
        loadingCoordinator.suppressArchivedThread(path = normalizedPath, provider = thread.provider, threadId = thread.id)
      }
      stateStore.removeThread(normalizedPath, thread.provider, thread.id)

      val threadIdentity = buildAgentSessionIdentity(thread.provider, thread.id)
      try {
        archiveChatCleanup(normalizedPath, threadIdentity)
      }
      catch (t: Throwable) {
        if (t is CancellationException) {
          throw t
        }
        // Archive is already successful at provider level; cleanup is best-effort and must not
        // resurrect the thread in UI by short-circuiting state update/refresh.
        LOG.warn("Failed to clean archived thread chat metadata for ${thread.provider}:${thread.id}", t)
      }

      if (thread.provider == AgentSessionProvider.CODEX) {
        delay(CODEX_ARCHIVE_REFRESH_DELAY)
      }
      refresh()
    }
  }

  private fun launchDropAction(
    key: String,
    droppedActionMessage: String,
    progress: SingleFlightProgressRequest? = null,
    block: suspend () -> Unit,
  ) {
    actionGate.launch(
      scope = serviceScope,
      key = key,
      policy = SingleFlightPolicy.DROP,
      progress = progress,
      onDrop = { LOG.debug(droppedActionMessage) },
      block = block,
    )
  }

  fun loadProjectThreadsOnDemand(path: String) {
    loadingCoordinator.loadProjectThreadsOnDemand(path)
  }

  fun loadWorktreeThreadsOnDemand(projectPath: String, worktreePath: String) {
    loadingCoordinator.loadWorktreeThreadsOnDemand(projectPath, worktreePath)
  }
}

private suspend fun openOrFocusProjectInternal(path: String) {
  val normalized = normalizeAgentWorkbenchPath(path)
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
  serviceScope: CoroutineScope,
) {
  val normalized = normalizeAgentWorkbenchPath(path)
  if (AgentChatOpenModeSettings.openInDedicatedFrame()) {
    openChatInDedicatedFrame(
      path = normalized,
      thread = thread,
      subAgent = subAgent,
      shellCommandOverride = shellCommandOverride,
      serviceScope = serviceScope,
    )
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

private fun markClaudeQuotaHintEligible(provider: AgentSessionProvider) {
  if (provider != AgentSessionProvider.CLAUDE) {
    return
  }
  service<AgentSessionsTreeUiStateService>().markClaudeQuotaHintEligible()
}

private fun buildCreateSessionActionKey(path: String, provider: AgentSessionProvider, mode: AgentSessionLaunchMode): String {
  return "$CREATE_SESSION_ACTION_KEY_PREFIX:$path:$provider:mode=$mode"
}

private fun buildOpenThreadActionKey(path: String, thread: AgentSessionThread): String {
  return "$OPEN_THREAD_ACTION_KEY_PREFIX:$path:${thread.provider}:${thread.id}"
}

private fun buildOpenSubAgentActionKey(path: String, thread: AgentSessionThread, subAgent: AgentSubAgent): String {
  return "$OPEN_SUB_AGENT_ACTION_KEY_PREFIX:$path:${thread.provider}:${thread.id}:${subAgent.id}"
}

private fun buildArchiveThreadActionKey(path: String, thread: AgentSessionThread): String {
  return "$ARCHIVE_THREAD_ACTION_KEY_PREFIX:$path:${thread.provider}:${thread.id}"
}

private fun logMissingProviderBridge(provider: AgentSessionProvider) {
  LOG.warn("No session provider bridge registered for ${provider.value}")
}

private fun dedicatedFrameOpenProgressRequest(currentProject: Project?): SingleFlightProgressRequest? {
  if (!AgentChatOpenModeSettings.openInDedicatedFrame()) return null
  return SingleFlightProgressRequest(
    project = currentOrDefaultProject(currentProject),
    title = AgentSessionsBundle.message("toolwindow.progress.opening.dedicated.frame"),
  )
}

private suspend fun openNewChat(path: String, identity: String, command: List<String>, serviceScope: CoroutineScope) {
  val title = AgentSessionsBundle.message("toolwindow.action.new.thread")
  val dedicatedFrame = AgentChatOpenModeSettings.openInDedicatedFrame()
  if (dedicatedFrame) {
    openNewChatInDedicatedFrame(path = path, identity = identity, command = command, title = title, serviceScope = serviceScope)
    return
  }
  val openProject = findOpenProject(path)
  if (openProject != null) {
    openNewChatInProject(project = openProject, projectPath = path, identity = identity, command = command, title = title)
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
        openNewChatInProject(project = project, projectPath = path, identity = identity, command = command, title = title)
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
  serviceScope: CoroutineScope,
) {
  val dedicatedProjectPath = AgentWorkbenchDedicatedFrameProjectManager.dedicatedProjectPath()
  val openProject = findOpenProject(dedicatedProjectPath)
  if (openProject != null) {
    AgentWorkbenchDedicatedFrameProjectManager.configureProject(openProject)
    openNewChatInProject(project = openProject, projectPath = path, identity = identity, command = command, title = title)
    return
  }

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
      val manager = RecentProjectsManager.getInstance() as? RecentProjectsManagerBase ?: return
      val projectPath2 = resolveProjectPath(manager, project)
      if (projectPath2 != dedicatedProjectPath) {
        return
      }
      serviceScope.launch {
        AgentWorkbenchDedicatedFrameProjectManager.configureProject(project)
        openNewChatInProject(project = project, projectPath = path, identity = identity, command = command, title = title)
        connection.disconnect()
      }
    }
  })
  openDedicatedFrameProject(dedicatedProjectDir, connection)
}

private suspend fun openNewChatInProject(
  project: Project,
  projectPath: String,
  identity: String,
  command: List<String>,
  title: String,
) {
    val threadId = resolveAgentSessionId(identity)
    openChat(
      project = project,
      projectPath = projectPath,
      threadIdentity = identity,
      shellCommand = command,
      threadId = threadId,
      threadTitle = title,
      subAgentId = null,
      threadActivity = AgentThreadActivity.READY,
    )
  withContext(Dispatchers.UI) {
    project.serviceAsync<ProjectUtilService>().focusProjectWindow()
  }
}

private suspend fun openChatInDedicatedFrame(
  path: String,
  thread: AgentSessionThread,
  subAgent: AgentSubAgent?,
  shellCommandOverride: List<String>?,
  serviceScope: CoroutineScope,
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
  openDedicatedFrameProject(dedicatedProjectDir, connection)
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
    openChat(
      project = project,
      projectPath = projectPath,
      threadIdentity = identity,
      shellCommand = shellCommandOverride ?: command,
      threadId = thread.id,
      threadTitle = thread.title,
      subAgentId = subAgent?.id,
      threadActivity = thread.activity,
    )
    ProjectUtilService.getInstance(project).focusProjectWindow()
  }
}

private suspend fun openDedicatedFrameProject(
  dedicatedProjectDir: Path,
  connection: SimpleMessageBusConnection,
) {
  try {
    val result = (serviceAsync<ProjectManager>() as ProjectManagerEx).openProjectAsync(
      projectIdentityFile = dedicatedProjectDir,
      options = OpenProjectTask {
        forceOpenInNewFrame = true
        runConfigurators = false
        createModule = false
      },
    )
    if (result == null) {
      connection.disconnect()
    }
  }
  catch (e: Throwable) {
    connection.disconnect()
    if (e is CancellationException) {
      throw e
    }
  }
}


private fun findOpenProject(path: String): Project? {
  val manager = RecentProjectsManager.getInstance() as? RecentProjectsManagerBase ?: return null
  val normalized = normalizeAgentWorkbenchPath(path)
  return ProjectManager.getInstance().openProjects.firstOrNull { project ->
    resolveProjectPath(manager, project) == normalized
  }
}

private fun resolveProjectPath(manager: RecentProjectsManagerBase, project: Project): String? {
  return manager.getProjectPath(project)?.invariantSeparatorsPathString
         ?: project.basePath?.let { normalizeAgentWorkbenchPath(it) }
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
