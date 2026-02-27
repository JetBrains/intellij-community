// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.service

// @spec community/plugins/agent-workbench/spec/agent-sessions.spec.md
// @spec community/plugins/agent-workbench/spec/agent-dedicated-frame.spec.md
// @spec community/plugins/agent-workbench/spec/actions/new-thread.spec.md

import com.intellij.agent.workbench.chat.AgentChatTabSelectionService
import com.intellij.agent.workbench.chat.closeAndForgetAgentChatsForThread
import com.intellij.agent.workbench.chat.openChat
import com.intellij.agent.workbench.common.AgentThreadActivity
import com.intellij.agent.workbench.common.normalizeAgentWorkbenchPath
import com.intellij.agent.workbench.common.parseAgentWorkbenchPathOrNull
import com.intellij.agent.workbench.sessions.AgentSessionsBundle
import com.intellij.agent.workbench.sessions.core.AgentSessionLaunchMode
import com.intellij.agent.workbench.sessions.core.AgentSessionProvider
import com.intellij.agent.workbench.sessions.core.AgentSessionThread
import com.intellij.agent.workbench.sessions.core.AgentSubAgent
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderBridges
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSource
import com.intellij.agent.workbench.sessions.frame.AGENT_SESSIONS_TOOL_WINDOW_ID
import com.intellij.agent.workbench.sessions.frame.AGENT_WORKBENCH_DEDICATED_FRAME_TYPE_ID
import com.intellij.agent.workbench.sessions.frame.AgentChatOpenModeSettings
import com.intellij.agent.workbench.sessions.frame.AgentWorkbenchDedicatedFrameProjectManager
import com.intellij.agent.workbench.sessions.model.AgentSessionProviderWarning
import com.intellij.agent.workbench.sessions.model.AgentSessionsState
import com.intellij.agent.workbench.sessions.model.ArchiveThreadTarget
import com.intellij.agent.workbench.sessions.model.ProjectEntry
import com.intellij.agent.workbench.sessions.state.AgentSessionsStateStore
import com.intellij.agent.workbench.sessions.state.AgentSessionsTreeUiStateService
import com.intellij.agent.workbench.sessions.state.InMemorySessionsTreeUiState
import com.intellij.agent.workbench.sessions.state.SessionsTreeUiState
import com.intellij.agent.workbench.sessions.util.SingleFlightActionGate
import com.intellij.agent.workbench.sessions.util.SingleFlightPolicy
import com.intellij.agent.workbench.sessions.util.SingleFlightProgressRequest
import com.intellij.agent.workbench.sessions.util.buildAgentSessionIdentity
import com.intellij.agent.workbench.sessions.util.buildAgentSessionNewIdentity
import com.intellij.agent.workbench.sessions.util.buildAgentSessionResumeCommand
import com.intellij.agent.workbench.sessions.util.isAgentSessionNewIdentity
import com.intellij.agent.workbench.sessions.util.isAgentSessionNewSessionId
import com.intellij.agent.workbench.sessions.util.parseAgentSessionIdentity
import com.intellij.agent.workbench.sessions.util.resolveAgentSessionId
import com.intellij.ide.impl.OpenProjectTask
import com.intellij.ide.impl.ProjectUtilService
import com.intellij.ide.util.PropertiesComponent
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
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
import com.intellij.project.ProjectStoreOwner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.nio.file.Path
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.time.Duration.Companion.seconds

private val LOG = logger<AgentSessionsService>()

private const val SUPPRESS_BRANCH_MISMATCH_DIALOG_KEY = "agent.workbench.suppress.branch.mismatch.dialog"
private const val OPEN_PROJECT_ACTION_KEY_PREFIX = "project-open"
private const val OPEN_DEDICATED_FRAME_ACTION_KEY_PREFIX = "dedicated-frame-open"
private const val CREATE_SESSION_ACTION_KEY_PREFIX = "session-create"
private const val OPEN_THREAD_ACTION_KEY_PREFIX = "thread-open"
private const val OPEN_SUB_AGENT_ACTION_KEY_PREFIX = "subagent-open"
private const val ARCHIVE_THREADS_ACTION_KEY_PREFIX = "threads-archive"
private const val UNARCHIVE_THREADS_ACTION_KEY_PREFIX = "threads-unarchive"
private const val AGENT_SESSIONS_NOTIFICATION_GROUP_ID = "Agent Workbench Sessions"
private const val PENDING_LAUNCH_MODE_STANDARD = "standard"
private const val PENDING_LAUNCH_MODE_YOLO = "yolo"
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
  private val stateStore = AgentSessionsStateStore()
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
            refreshCatalogAndLoadNewlyOpened()
          }

          override fun projectClosed(project: Project) {
            refreshCatalogAndLoadNewlyOpened()
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

  fun refresh() {
    loadingCoordinator.refresh()
  }

  internal fun refreshCatalogAndLoadNewlyOpened() {
    loadingCoordinator.refreshCatalogAndLoadNewlyOpened()
  }

  fun openOrFocusProject(path: String) {
    val normalizedPath = normalizeAgentWorkbenchPath(path)
    launchDropAction(
      key = buildOpenProjectActionKey(normalizedPath),
      droppedActionMessage = "Dropped duplicate open project action for $normalizedPath",
    ) {
      openOrFocusProjectInternal(normalizedPath)
    }
  }

  fun openOrFocusDedicatedFrame(currentProject: Project? = null) {
    launchDropAction(
      key = OPEN_DEDICATED_FRAME_ACTION_KEY_PREFIX,
      droppedActionMessage = "Dropped duplicate open dedicated frame action",
      progress = dedicatedFrameOpenProgressRequest(currentProject),
    ) {
      openOrFocusDedicatedFrameInternal()
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

  fun canArchiveProvider(provider: AgentSessionProvider): Boolean {
    val bridge = AgentSessionProviderBridges.find(provider) ?: return false
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
      openChat(normalizedPath = normalizedPath, thread = thread, subAgent = null)
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
      openChat(normalizedPath = normalizedPath, thread = thread, subAgent = subAgent)
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

      openNewChat(normalizedPath = normalizedPath, identity = identity, command = launchSpec.command)
      if (provider == AgentSessionProvider.CODEX) {
        loadingCoordinator.refreshProviderScope(provider = provider, scopedPaths = setOf(normalizedPath))
      }
    }
  }

  fun archiveThread(path: String, provider: AgentSessionProvider, threadId: String) {
    archiveThreads(listOf(ArchiveThreadTarget(path = path, provider = provider, threadId = threadId)))
  }

  fun archiveThreads(targets: List<ArchiveThreadTarget>) {
    val normalizedTargets = normalizeArchiveTargets(targets)
    if (normalizedTargets.isEmpty()) {
      return
    }
    launchDropAction(
      key = buildArchiveThreadsActionKey(normalizedTargets),
      droppedActionMessage = "Dropped duplicate archive threads action for ${normalizedTargets.size} targets",
    ) {
      val outcome = archiveTargetsInternal(normalizedTargets)
      if (outcome.archivedTargets.isEmpty()) {
        return@launchDropAction
      }
      if (outcome.requiresCodexRefreshDelay) {
        delay(CODEX_ARCHIVE_REFRESH_DELAY)
      }
      refresh()
      showArchiveNotification(outcome)
    }
  }

  internal fun unarchiveThreads(targets: List<ArchiveThreadTarget>) {
    val normalizedTargets = normalizeArchiveTargets(targets)
    if (normalizedTargets.isEmpty()) {
      return
    }
    launchDropAction(
      key = buildUnarchiveThreadsActionKey(normalizedTargets),
      droppedActionMessage = "Dropped duplicate unarchive threads action for ${normalizedTargets.size} targets",
    ) {
      var anyUnarchived = false
      var requiresCodexRefreshDelay = false
      normalizedTargets.forEach { target ->
        val provider = target.provider
        val bridge = AgentSessionProviderBridges.find(provider)
        if (bridge == null) {
          logMissingProviderBridge(provider)
          return@forEach
        }
        if (!bridge.supportsUnarchiveThread) {
          return@forEach
        }

        val unarchived = try {
          bridge.unarchiveThread(path = target.path, threadId = target.threadId)
        }
        catch (t: Throwable) {
          if (t is CancellationException) {
            throw t
          }
          LOG.warn("Failed to unarchive thread ${provider}:${target.threadId}", t)
          false
        }
        if (!unarchived) {
          return@forEach
        }

        anyUnarchived = true
        if (provider == AgentSessionProvider.CODEX) {
          loadingCoordinator.unsuppressArchivedThread(path = target.path, provider = provider, threadId = target.threadId)
          requiresCodexRefreshDelay = true
        }
      }
      if (!anyUnarchived) {
        return@launchDropAction
      }
      if (requiresCodexRefreshDelay) {
        delay(CODEX_ARCHIVE_REFRESH_DELAY)
      }
      refresh()
    }
  }

  private suspend fun archiveTargetsInternal(targets: List<ArchiveThreadTarget>): ArchiveBatchOutcome {
    val archivedTargets = ArrayList<ArchiveThreadTarget>(targets.size)
    val undoTargets = ArrayList<ArchiveThreadTarget>()
    var requiresCodexRefreshDelay = false

    targets.forEach { target ->
      val provider = target.provider

      if (provider == AgentSessionProvider.CODEX && isAgentSessionNewSessionId(target.threadId)) {
        stateStore.removeThread(target.path, provider, target.threadId)
        val threadIdentity = buildAgentSessionIdentity(provider, target.threadId)
        try {
          archiveChatCleanup(target.path, threadIdentity)
        }
        catch (t: Throwable) {
          if (t is CancellationException) {
            throw t
          }
          LOG.warn("Failed to clean archived pending thread chat metadata for ${provider}:${target.threadId}", t)
        }
        archivedTargets.add(target)
        return@forEach
      }

      val bridge = AgentSessionProviderBridges.find(provider)
      if (bridge == null) {
        logMissingProviderBridge(provider)
        loadingCoordinator.appendProviderUnavailableWarning(target.path, provider)
        return@forEach
      }
      if (!bridge.supportsArchiveThread) {
        return@forEach
      }

      val archived = try {
        bridge.archiveThread(path = target.path, threadId = target.threadId)
      }
      catch (t: Throwable) {
        if (t is CancellationException) {
          throw t
        }
        LOG.warn("Failed to archive thread ${provider}:${target.threadId}", t)
        loadingCoordinator.appendProviderUnavailableWarning(target.path, provider)
        return@forEach
      }

      if (!archived) {
        loadingCoordinator.appendProviderUnavailableWarning(target.path, provider)
        return@forEach
      }

      if (provider == AgentSessionProvider.CODEX) {
        loadingCoordinator.suppressArchivedThread(path = target.path, provider = provider, threadId = target.threadId)
        requiresCodexRefreshDelay = true
      }
      stateStore.removeThread(target.path, provider, target.threadId)

      val threadIdentity = buildAgentSessionIdentity(provider, target.threadId)
      try {
        archiveChatCleanup(target.path, threadIdentity)
      }
      catch (t: Throwable) {
        if (t is CancellationException) {
          throw t
        }
        // Archive is already successful at provider level; cleanup is best-effort and must not
        // resurrect the thread in UI by short-circuiting state update/refresh.
        LOG.warn("Failed to clean archived thread chat metadata for ${provider}:${target.threadId}", t)
      }

      archivedTargets.add(target)
      if (bridge.supportsUnarchiveThread) {
        undoTargets.add(target)
      }
    }

    return ArchiveBatchOutcome(
      archivedTargets = archivedTargets,
      undoTargets = undoTargets,
      requiresCodexRefreshDelay = requiresCodexRefreshDelay,
    )
  }

  private fun normalizeArchiveTargets(targets: List<ArchiveThreadTarget>): List<ArchiveThreadTarget> {
    val normalizedByKey = LinkedHashMap<String, ArchiveThreadTarget>()
    targets.forEach { target ->
      val normalizedPath = normalizeAgentWorkbenchPath(target.path)
      val normalizedTarget = if (normalizedPath == target.path) target else target.copy(path = normalizedPath)
      val key = archiveTargetKey(normalizedTarget)
      normalizedByKey.putIfAbsent(key, normalizedTarget)
    }
    return normalizedByKey.values.toList()
  }

  private fun showArchiveNotification(outcome: ArchiveBatchOutcome) {
    if (ApplicationManager.getApplication().isUnitTestMode) {
      return
    }
    runCatching {
      val notification = NotificationGroupManager.getInstance()
        .getNotificationGroup(AGENT_SESSIONS_NOTIFICATION_GROUP_ID)
        .createNotification(
          AgentSessionsBundle.message("toolwindow.notification.archive.title"),
          AgentSessionsBundle.message("toolwindow.notification.archive.body", outcome.archivedTargets.size),
          NotificationType.INFORMATION,
        )
      if (outcome.undoTargets.isNotEmpty()) {
        val undoTargets = outcome.undoTargets.toList()
        notification.addAction(
          NotificationAction.createSimpleExpiring(
            AgentSessionsBundle.message("toolwindow.notification.archive.undo"),
          ) {
            unarchiveThreads(undoTargets)
          }
        )
      }
      notification.notify(null)
    }.onFailure { error ->
      LOG.warn("Failed to show Agent Threads archive notification", error)
    }
  }

private data class ArchiveBatchOutcome(
  @JvmField val archivedTargets: List<ArchiveThreadTarget>,
  @JvmField val undoTargets: List<ArchiveThreadTarget>,
  @JvmField val requiresCodexRefreshDelay: Boolean,
)

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

private data class PendingCodexMetadata(
  @JvmField val createdAtMs: Long,
  @JvmField val launchMode: String,
)

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

private suspend fun openOrFocusProjectInternal(normalizedPath: String) {
  val project = openProject(normalizedPath) ?: return
  val projectUtilService = project.serviceAsync<ProjectUtilService>()
  withContext(Dispatchers.UI) {
    projectUtilService.focusProjectWindow()
  }
}

private suspend fun openOrFocusDedicatedFrameInternal() {
  val dedicatedProjectPath = AgentWorkbenchDedicatedFrameProjectManager.dedicatedProjectPath()
  val openProject = findOpenProject(dedicatedProjectPath)
  if (openProject != null) {
    AgentWorkbenchDedicatedFrameProjectManager.configureProject(openProject)
    focusProjectWindowAndActivateSessions(openProject)
    return
  }

  val dedicatedProjectDir = try {
    AgentWorkbenchDedicatedFrameProjectManager.ensureProjectPath()
  }
  catch (e: CancellationException) {
    throw e
  }
  catch (e: Throwable) {
    LOG.warn("Failed to prepare dedicated chat frame project", e)
    return
  }

  val dedicatedProject = openDedicatedFrameProject(dedicatedProjectDir) ?: return
  AgentWorkbenchDedicatedFrameProjectManager.configureProject(dedicatedProject)
  focusProjectWindowAndActivateSessions(dedicatedProject)
}

private suspend fun openChat(
  normalizedPath: String,
  thread: AgentSessionThread,
  subAgent: AgentSubAgent?,
  shellCommandOverride: List<String>? = null,
) {
  if (AgentChatOpenModeSettings.openInDedicatedFrame()) {
    openChatInDedicatedFrame(
      normalizedPath = normalizedPath,
      thread = thread,
      subAgent = subAgent,
      shellCommandOverride = shellCommandOverride,
    )
    return
  }
  val openProject = openProject(normalizedPath) ?: return
  openChatInProject(openProject, normalizedPath, thread, subAgent, shellCommandOverride)
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

private fun buildArchiveThreadsActionKey(targets: List<ArchiveThreadTarget>): String {
  val targetsKey = targets.map(::archiveTargetKey).sorted().joinToString("|")
  return "$ARCHIVE_THREADS_ACTION_KEY_PREFIX:$targetsKey"
}

private fun buildUnarchiveThreadsActionKey(targets: List<ArchiveThreadTarget>): String {
  val targetsKey = targets.map(::archiveTargetKey).sorted().joinToString("|")
  return "$UNARCHIVE_THREADS_ACTION_KEY_PREFIX:$targetsKey"
}

private fun archiveTargetKey(target: ArchiveThreadTarget): String {
  return "${target.path}:${target.provider}:${target.threadId}"
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

private fun resolvePendingCodexMetadata(identity: String, command: List<String>): PendingCodexMetadata? {
  if (!isAgentSessionNewIdentity(identity)) {
    return null
  }
  val provider = parseAgentSessionIdentity(identity)?.provider ?: return null
  if (provider != AgentSessionProvider.CODEX) {
    return null
  }
  val launchMode = if ("--full-auto" in command) PENDING_LAUNCH_MODE_YOLO else PENDING_LAUNCH_MODE_STANDARD
  return PendingCodexMetadata(
    createdAtMs = System.currentTimeMillis(),
    launchMode = launchMode,
  )
}

private suspend fun openNewChat(normalizedPath: String, identity: String, command: List<String>) {
  val title = AgentSessionsBundle.message("toolwindow.action.new.thread")
  val dedicatedFrame = AgentChatOpenModeSettings.openInDedicatedFrame()
  if (dedicatedFrame) {
    openNewChatInDedicatedFrame(normalizedPath = normalizedPath, identity = identity, command = command, title = title)
    return
  }
  val openProject = openProject(normalizedPath) ?: return
  openNewChatInProject(project = openProject, projectPath = normalizedPath, identity = identity, command = command, title = title)
}

private suspend fun openNewChatInDedicatedFrame(
  normalizedPath: String,
  identity: String,
  command: List<String>,
  title: String,
) {
  val dedicatedProjectPath = AgentWorkbenchDedicatedFrameProjectManager.dedicatedProjectPath()
  val openProject = findOpenProject(dedicatedProjectPath)
  if (openProject != null) {
    AgentWorkbenchDedicatedFrameProjectManager.configureProject(openProject)
    openNewChatInProject(project = openProject, projectPath = normalizedPath, identity = identity, command = command, title = title)
    return
  }

  val dedicatedProjectDir = try {
    AgentWorkbenchDedicatedFrameProjectManager.ensureProjectPath()
  }
  catch (e: CancellationException) {
    throw e
  }
  catch (e: Throwable) {
    LOG.warn("Failed to prepare dedicated chat frame project", e)
    return
  }

  val dedicatedProject = openDedicatedFrameProject(dedicatedProjectDir) ?: return
  AgentWorkbenchDedicatedFrameProjectManager.configureProject(dedicatedProject)
  openNewChatInProject(project = dedicatedProject, projectPath = normalizedPath, identity = identity, command = command, title = title)
}

private suspend fun openNewChatInProject(
  project: Project,
  projectPath: String,
  identity: String,
  command: List<String>,
  title: String,
) {
  val threadId = resolveAgentSessionId(identity)
  val pendingMetadata = resolvePendingCodexMetadata(identity = identity, command = command)
  openChat(
    project = project,
    projectPath = projectPath,
    threadIdentity = identity,
    shellCommand = command,
    threadId = threadId,
    threadTitle = title,
    subAgentId = null,
    threadActivity = AgentThreadActivity.READY,
    pendingCreatedAtMs = pendingMetadata?.createdAtMs,
    pendingLaunchMode = pendingMetadata?.launchMode,
  )
  focusProjectWindow(project)
}

private suspend fun openChatInDedicatedFrame(
  normalizedPath: String,
  thread: AgentSessionThread,
  subAgent: AgentSubAgent?,
  shellCommandOverride: List<String>?,
) {
  val dedicatedProjectPath = AgentWorkbenchDedicatedFrameProjectManager.dedicatedProjectPath()
  val openProject = findOpenProject(dedicatedProjectPath)
  if (openProject != null) {
    AgentWorkbenchDedicatedFrameProjectManager.configureProject(openProject)
    openChatInProject(openProject, normalizedPath, thread, subAgent, shellCommandOverride)
    return
  }

  val dedicatedProjectDir = try {
    AgentWorkbenchDedicatedFrameProjectManager.ensureProjectPath()
  }
  catch (e: Throwable) {
    LOG.warn("Failed to prepare dedicated chat frame project", e)
    return
  }

  val dedicatedProject = openDedicatedFrameProject(dedicatedProjectDir) ?: return
  AgentWorkbenchDedicatedFrameProjectManager.configureProject(dedicatedProject)
  openChatInProject(dedicatedProject, normalizedPath, thread, subAgent, shellCommandOverride)
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
    focusProjectWindowSync(project)
  }
}

private suspend fun focusProjectWindow(project: Project) {
  withContext(Dispatchers.UI) {
    project.serviceAsync<ProjectUtilService>().focusProjectWindow()
  }
}

private fun focusProjectWindowSync(project: Project) {
  ProjectUtilService.getInstance(project).focusProjectWindow()
}

private suspend fun focusProjectWindowAndActivateSessions(project: Project) {
  withContext(Dispatchers.UI) {
    project.serviceAsync<ProjectUtilService>().focusProjectWindow()
    ToolWindowManager.getInstance(project).getToolWindow(AGENT_SESSIONS_TOOL_WINDOW_ID)?.activate(null)
  }
}

private suspend fun openDedicatedFrameProject(dedicatedProjectDir: Path): Project? {
  return openProject(
    normalizedPath = dedicatedProjectDir.invariantSeparatorsPathString,
    options = OpenProjectTask {
      forceOpenInNewFrame = true
      runConfigurators = false
      runConversionBeforeOpen = false
      preloadServices = false
      preventIprLookup = true
      showWelcomeScreen = false
      createModule = false
      projectFrameTypeId = AGENT_WORKBENCH_DEDICATED_FRAME_TYPE_ID
    },
  )
}

private suspend fun openProject(
  normalizedPath: String,
  options: OpenProjectTask = OpenProjectTask(),
): Project? {
  val projectPath = parseAgentWorkbenchPathOrNull(normalizedPath) ?: return null
  findOpenProject(normalizedPath)?.let { return it }

  try {
    return (serviceAsync<ProjectManager>() as ProjectManagerEx).openProjectAsync(
      projectIdentityFile = projectPath,
      options = options,
    )
  }
  catch (e: CancellationException) {
    throw e
  }
  catch (e: Throwable) {
    LOG.warn("Failed to open project at $normalizedPath", e)
    return null
  }
}

private fun findOpenProject(normalizedPath: String): Project? {
  return ProjectManager.getInstance().openProjects.firstOrNull { project ->
    val projectPath = (project as? ProjectStoreOwner)
      ?.componentStore
      ?.storeDescriptor
      ?.presentableUrl
      ?.invariantSeparatorsPathString
      ?: return@firstOrNull false
    projectPath == normalizedPath
  }
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
  @JvmField val threads: List<AgentSessionThread>,
  @JvmField val errorMessage: String? = null,
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
