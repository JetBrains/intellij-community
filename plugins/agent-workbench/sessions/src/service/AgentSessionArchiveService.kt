// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.service

// @spec community/plugins/agent-workbench/spec/core/agent-workbench-telemetry.spec.md

import com.intellij.agent.workbench.chat.closeAndForgetAgentChatsForThread
import com.intellij.agent.workbench.common.normalizeAgentWorkbenchPath
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.common.session.AgentSessionThread
import com.intellij.agent.workbench.sessions.AgentSessionsBundle
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderDescriptor
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviders
import com.intellij.agent.workbench.sessions.core.statistics.AgentWorkbenchEntryPoint
import com.intellij.agent.workbench.sessions.core.statistics.AgentWorkbenchTelemetry
import com.intellij.agent.workbench.sessions.frame.AgentWorkbenchDedicatedFrameProjectManager
import com.intellij.agent.workbench.sessions.model.ArchiveThreadTarget
import com.intellij.agent.workbench.sessions.model.archiveThreadTargetKey
import com.intellij.agent.workbench.sessions.model.normalizeArchiveThreadTarget
import com.intellij.agent.workbench.sessions.state.AgentSessionWarmStateService
import com.intellij.agent.workbench.sessions.util.SingleFlightActionGate
import com.intellij.agent.workbench.sessions.util.SingleFlightPolicy
import com.intellij.agent.workbench.sessions.util.buildAgentSessionIdentity
import com.intellij.agent.workbench.sessions.util.isAgentSessionNewSessionId
import com.intellij.ide.RecentProjectsManager
import com.intellij.ide.RecentProjectsManagerBase
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.ide.progress.TaskCancellation
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.platform.util.progress.reportSequentialProgress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

private val LOG = logger<AgentSessionArchiveService>()

private const val ARCHIVE_THREADS_ACTION_KEY_PREFIX = "threads-archive"
private const val UNARCHIVE_THREADS_ACTION_KEY_PREFIX = "threads-unarchive"
private const val AGENT_SESSIONS_NOTIFICATION_GROUP_ID = "Agent Workbench Sessions"

@Service(Service.Level.APP)
class AgentSessionArchiveService internal constructor(
  private val serviceScope: CoroutineScope,
  private val syncService: AgentSessionRefreshService,
  private val contentRepository: AgentSessionContentRepository,
  private val archiveChatCleanup: suspend (projectPath: String, threadIdentity: String, subAgentId: String?) -> Unit,
  private val backgroundTaskRunner: AgentSessionArchiveBackgroundTaskRunner,
  private val archivedSessionsRefreshIfLoaded: () -> Unit = {},
) {
  @Suppress("unused")
  constructor(serviceScope: CoroutineScope) : this(
    serviceScope = serviceScope,
    syncService = service<AgentSessionRefreshService>(),
    contentRepository = AgentSessionContentRepository(
      stateStore = service(),
      warmState = service<AgentSessionWarmStateService>(),
    ),
    archiveChatCleanup = { projectPath, threadIdentity, subAgentId ->
      closeAndForgetAgentChatsForThread(projectPath = projectPath, threadIdentity = threadIdentity, subAgentId = subAgentId)
    },
    backgroundTaskRunner = IdeAgentSessionArchiveBackgroundTaskRunner,
    archivedSessionsRefreshIfLoaded = { service<AgentArchivedSessionsService>().refreshIfLoaded() },
  )

  private val actionGate = SingleFlightActionGate()

  fun canArchiveProvider(provider: AgentSessionProvider): Boolean {
    val descriptor = AgentSessionProviders.find(provider) ?: return false
    return descriptor.supportsArchiveThread
  }

  fun canUnarchiveProvider(provider: AgentSessionProvider): Boolean {
    val descriptor = AgentSessionProviders.find(provider) ?: return false
    return descriptor.supportsUnarchiveThread
  }

  fun archiveThreads(
    targets: List<ArchiveThreadTarget>,
    entryPoint: AgentWorkbenchEntryPoint,
    preferredSingleArchivedLabel: @NlsSafe String? = null,
  ) {
    val normalizedTargets = normalizeArchiveTargets(targets)
    if (normalizedTargets.isEmpty()) {
      return
    }
    launchDropAction(
      key = buildArchiveThreadsActionKey(normalizedTargets),
      droppedActionMessage = "Dropped duplicate archive threads action for ${normalizedTargets.size} targets",
    ) {
      AgentWorkbenchTelemetry.logThreadArchiveRequested(entryPoint, normalizedTargets.singleProviderOrNull())
      val preparedBatch = prepareArchiveTargets(normalizedTargets, preferredSingleArchivedLabel)
      if (preparedBatch.providerTargets.isEmpty()) {
        finishArchiveBatch(preparedBatch.localOutcome)
        return@launchDropAction
      }
      val progressProject = resolveArchiveProgressProject(normalizedTargets)
      backgroundTaskRunner.run(progressProject, buildArchiveProgressTitle(preparedBatch.providerTargets.size)) {
        val providerOutcome = archivePreparedTargets(preparedBatch.providerTargets)
        finishArchiveBatch(preparedBatch.localOutcome + providerOutcome)
      }
    }
  }

  fun unarchiveThreads(targets: List<ArchiveThreadTarget>) {
    val normalizedTargets = normalizeArchiveTargets(targets)
    if (normalizedTargets.isEmpty()) {
      return
    }
    launchDropAction(
      key = buildUnarchiveThreadsActionKey(normalizedTargets),
      droppedActionMessage = "Dropped duplicate unarchive threads action for ${normalizedTargets.size} targets",
    ) {
      var anyUnarchived = false
      var refreshDelayMs = 0L
      normalizedTargets.forEach { target ->
        val provider = target.provider
        val descriptor = AgentSessionProviders.find(provider)
        if (descriptor == null) {
          logMissingProviderDescriptor(provider)
          return@forEach
        }
        if (!descriptor.supportsUnarchiveThread) {
          return@forEach
        }

        val unarchived = try {
          descriptor.unarchiveThread(path = target.path, threadId = target.threadId)
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
        if (descriptor.suppressArchivedThreadsDuringRefresh) {
          syncService.unsuppressArchivedTarget(target)
        }
        refreshDelayMs = maxOf(refreshDelayMs, descriptor.archiveRefreshDelayMs)
      }
      if (!anyUnarchived) {
        return@launchDropAction
      }
      if (refreshDelayMs > 0L) {
        delay(refreshDelayMs.milliseconds)
      }
      syncService.refresh()
      archivedSessionsRefreshIfLoaded()
    }
  }

  private suspend fun prepareArchiveTargets(
    targets: List<ArchiveThreadTarget>,
    preferredSingleArchivedLabel: @NlsSafe String?,
  ): PreparedArchiveBatch {
    val singleArchivedLabel = preferredSingleArchivedLabel?.takeIf(String::isNotBlank)
                              ?: targets.singleOrNull()?.let(contentRepository::findArchiveNotificationLabel)
    val archivedTargets = ArrayList<ArchiveThreadTarget>(targets.size)
    val undoTargets = ArrayList<ArchiveThreadTarget>()
    val providerTargets = ArrayList<PreparedArchiveTarget>(targets.size)

    targets.forEach { target ->
      val provider = target.provider
      val cleanupTarget = target.toArchivedChatCleanupTarget()

      if (target.isPendingThread()) {
        contentRepository.removeArchivedTarget(target)
        try {
          archiveChatCleanup(target.path, cleanupTarget.threadIdentity, cleanupTarget.subAgentId)
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

      val descriptor = AgentSessionProviders.find(provider)
      if (descriptor == null) {
        logMissingProviderDescriptor(provider)
        syncService.appendProviderUnavailableWarning(target.path, provider)
        return@forEach
      }
      if (!descriptor.supportsArchiveThread) {
        return@forEach
      }

      val suppressed = descriptor.suppressArchivedThreadsDuringRefresh
      val rollbackThread = contentRepository.findArchivedTargetThread(target)
      if (suppressed) {
        syncService.suppressArchivedTarget(target)
      }
      contentRepository.removeArchivedTarget(target)
      providerTargets.add(
        PreparedArchiveTarget(
          target = target,
          descriptor = descriptor,
          cleanupTarget = cleanupTarget,
          suppressed = suppressed,
          rollbackThread = rollbackThread,
        )
      )
    }

    return PreparedArchiveBatch(
      providerTargets = providerTargets,
      localOutcome = ArchiveBatchOutcome(
        requestedCount = targets.size,
        archivedTargets = archivedTargets,
        singleArchivedLabel = singleArchivedLabel,
        undoTargets = undoTargets,
        refreshDelayMs = 0L,
      ),
    )
  }

  private suspend fun archivePreparedTargets(targets: List<PreparedArchiveTarget>): ArchiveBatchOutcome {
    val archivedTargets = ArrayList<ArchiveThreadTarget>(targets.size)
    val undoTargets = ArrayList<ArchiveThreadTarget>()
    var refreshDelayMs = 0L

    reportSequentialProgress(targets.size) { reporter ->
      targets.forEachIndexed { index, preparedTarget ->
        reporter.itemStep(buildArchiveProgressStepText(current = index + 1, total = targets.size)) {
          val target = preparedTarget.target
          val provider = target.provider
          val descriptor = preparedTarget.descriptor
          val archived = try {
            if (descriptor.closeOpenChatBeforeArchiveThread) {
              archiveChatCleanup(target.path, preparedTarget.cleanupTarget.threadIdentity, preparedTarget.cleanupTarget.subAgentId)
            }
            descriptor.archiveThread(path = target.path, threadId = target.threadId)
          }
          catch (t: Throwable) {
            if (t is CancellationException) {
              throw t
            }
            LOG.warn("Failed to archive thread ${provider}:${target.threadId}", t)
            handleArchiveFailure(preparedTarget)
            return@itemStep
          }

          if (!archived) {
            handleArchiveFailure(preparedTarget)
            return@itemStep
          }

          refreshDelayMs = maxOf(refreshDelayMs, descriptor.archiveRefreshDelayMs)

          try {
            archiveChatCleanup(target.path, preparedTarget.cleanupTarget.threadIdentity, preparedTarget.cleanupTarget.subAgentId)
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
          if (descriptor.supportsUnarchiveThread) {
            undoTargets.add(target)
          }
        }
      }
    }

    return ArchiveBatchOutcome(
      requestedCount = targets.size,
      archivedTargets = archivedTargets,
      singleArchivedLabel = null,
      undoTargets = undoTargets,
      refreshDelayMs = refreshDelayMs,
    )
  }

  private fun handleArchiveFailure(preparedTarget: PreparedArchiveTarget) {
    if (preparedTarget.suppressed) {
      syncService.unsuppressArchivedTarget(preparedTarget.target)
    }
    preparedTarget.rollbackThread?.let { thread ->
      contentRepository.restoreArchivedThread(preparedTarget.target.path, thread)
    }
    syncService.appendProviderUnavailableWarning(preparedTarget.target.path, preparedTarget.target.provider)
    syncService.refreshProviderForPath(preparedTarget.target.path, preparedTarget.target.provider)
  }

  private suspend fun finishArchiveBatch(outcome: ArchiveBatchOutcome) {
    if (outcome.archivedTargets.isEmpty()) {
      return
    }
    if (outcome.refreshDelayMs > 0L) {
      delay(outcome.refreshDelayMs.milliseconds)
    }
    syncService.refresh()
    archivedSessionsRefreshIfLoaded()
    showArchiveNotification(outcome)
  }

  private fun normalizeArchiveTargets(targets: List<ArchiveThreadTarget>): List<ArchiveThreadTarget> {
    val normalizedByKey = LinkedHashMap<String, ArchiveThreadTarget>()
    targets.forEach { target ->
      val normalizedTarget = normalizeArchiveThreadTarget(target)
      val key = archiveThreadTargetKey(normalizedTarget)
      normalizedByKey.putIfAbsent(key, normalizedTarget)
    }
    return normalizedByKey.values.toList()
  }

  private fun resolveArchiveProgressProject(targets: List<ArchiveThreadTarget>): Project {
    val targetPaths = targets.mapTo(LinkedHashSet()) { target -> normalizeAgentWorkbenchPath(target.path) }
    val openProject = resolveOpenProjectForArchivePaths(targetPaths)
    return openProject ?: ProjectManager.getInstance().defaultProject
  }

  private fun List<ArchiveThreadTarget>.singleProviderOrNull(): AgentSessionProvider? {
    return map(ArchiveThreadTarget::provider).distinct().singleOrNull()
  }

  private fun showArchiveNotification(outcome: ArchiveBatchOutcome) {
    if (ApplicationManager.getApplication().isUnitTestMode) {
      return
    }
    runCatching {
      val presentation = buildArchiveNotificationPresentation(
        requestedCount = outcome.requestedCount,
        archivedCount = outcome.archivedTargets.size,
        singleArchivedLabel = outcome.singleArchivedLabel,
        canUndo = outcome.undoTargets.isNotEmpty(),
      )
      val notification = NotificationGroupManager.getInstance()
        .getNotificationGroup(AGENT_SESSIONS_NOTIFICATION_GROUP_ID)
        .createNotification(
          presentation.title,
          presentation.body,
          NotificationType.INFORMATION,
        )
      if (presentation.showUndoAction) {
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

  private fun launchDropAction(
    key: String,
    droppedActionMessage: String,
    policy: SingleFlightPolicy = SingleFlightPolicy.DROP,
    block: suspend () -> Unit,
  ) {
    actionGate.launch(
      scope = serviceScope,
      key = key,
      policy = policy,
      onDrop = { LOG.debug(droppedActionMessage) },
      block = block,
    )
  }
}

private data class ArchiveBatchOutcome(
  @JvmField val requestedCount: Int,
  @JvmField val archivedTargets: List<ArchiveThreadTarget>,
  @JvmField val singleArchivedLabel: String?,
  @JvmField val undoTargets: List<ArchiveThreadTarget>,
  @JvmField val refreshDelayMs: Long,
)

private operator fun ArchiveBatchOutcome.plus(other: ArchiveBatchOutcome): ArchiveBatchOutcome {
  return ArchiveBatchOutcome(
    requestedCount = requestedCount,
    archivedTargets = archivedTargets + other.archivedTargets,
    singleArchivedLabel = singleArchivedLabel,
    undoTargets = undoTargets + other.undoTargets,
    refreshDelayMs = maxOf(refreshDelayMs, other.refreshDelayMs),
  )
}

internal fun interface AgentSessionArchiveBackgroundTaskRunner {
  suspend fun run(project: Project, title: @NlsContexts.ProgressTitle String, block: suspend () -> Unit)
}

private object IdeAgentSessionArchiveBackgroundTaskRunner : AgentSessionArchiveBackgroundTaskRunner {
  override suspend fun run(project: Project, title: @NlsContexts.ProgressTitle String, block: suspend () -> Unit) {
    withBackgroundProgress(
      project = project,
      title = title,
      cancellation = TaskCancellation.nonCancellable(),
      suspender = null,
      visibleInStatusBar = true,
    ) {
      block()
    }
  }
}

private fun buildArchiveProgressTitle(count: Int): @NlsContexts.ProgressTitle String {
  return if (count == 1) {
    AgentSessionsBundle.message("toolwindow.progress.archiving.thread")
  }
  else {
    AgentSessionsBundle.message("toolwindow.progress.archiving.threads")
  }
}

private fun buildArchiveProgressStepText(current: Int, total: Int): @NlsContexts.ProgressText String {
  return if (total == 1) {
    AgentSessionsBundle.message("toolwindow.progress.archiving.thread")
  }
  else {
    AgentSessionsBundle.message("toolwindow.progress.archiving.thread.step", current, total)
  }
}

private fun resolveOpenProjectForArchivePaths(targetPaths: Set<String>): Project? {
  if (targetPaths.isEmpty()) {
    return null
  }
  val manager = RecentProjectsManager.getInstance() as? RecentProjectsManagerBase
  val openProjects = ProjectManager.getInstance().openProjects
    .filterNot { project -> AgentWorkbenchDedicatedFrameProjectManager.isDedicatedProject(project) }
  if (openProjects.isEmpty()) {
    return null
  }

  for (targetPath in targetPaths) {
    val directMatch = openProjects.firstOrNull { project ->
      resolveOpenProjectPath(
        managerProjectPath = manager?.getProjectPath(project),
        projectBasePath = project.basePath,
      ) == targetPath
    }
    if (directMatch != null) {
      return directMatch
    }
  }
  return openProjects.firstOrNull()
}

private data class PreparedArchiveBatch(
  @JvmField val providerTargets: List<PreparedArchiveTarget>,
  @JvmField val localOutcome: ArchiveBatchOutcome,
)

private data class PreparedArchiveTarget(
  @JvmField val target: ArchiveThreadTarget,
  @JvmField val descriptor: AgentSessionProviderDescriptor,
  @JvmField val cleanupTarget: ArchivedChatCleanupTarget,
  @JvmField val suppressed: Boolean,
  @JvmField val rollbackThread: AgentSessionThread?,
)

internal data class ArchiveNotificationPresentation(
  @JvmField val title: @NlsContexts.NotificationTitle String,
  @JvmField val body: @NlsContexts.NotificationContent String,
  @JvmField val showUndoAction: Boolean,
)

private data class ArchivedChatCleanupTarget(
  @JvmField val threadIdentity: String,
  @JvmField val subAgentId: String?,
)

private fun buildArchiveThreadsActionKey(targets: List<ArchiveThreadTarget>): String {
  val targetsKey = targets.map(::archiveThreadTargetKey).sorted().joinToString("|")
  return "$ARCHIVE_THREADS_ACTION_KEY_PREFIX:$targetsKey"
}

private fun buildUnarchiveThreadsActionKey(targets: List<ArchiveThreadTarget>): String {
  val targetsKey = targets.map(::archiveThreadTargetKey).sorted().joinToString("|")
  return "$UNARCHIVE_THREADS_ACTION_KEY_PREFIX:$targetsKey"
}

private fun logMissingProviderDescriptor(provider: AgentSessionProvider) {
  LOG.warn("No session provider registered for ${provider.value}")
}

private fun ArchiveThreadTarget.toArchivedChatCleanupTarget(): ArchivedChatCleanupTarget {
  return when (this) {
    is ArchiveThreadTarget.Thread -> ArchivedChatCleanupTarget(
      threadIdentity = buildAgentSessionIdentity(provider, threadId),
      subAgentId = null,
    )

    is ArchiveThreadTarget.SubAgent -> ArchivedChatCleanupTarget(
      threadIdentity = buildAgentSessionIdentity(provider, parentThreadId),
      subAgentId = subAgentId,
    )
  }
}

private fun ArchiveThreadTarget.isPendingThread(): Boolean {
  return this is ArchiveThreadTarget.Thread && isAgentSessionNewSessionId(threadId)
}

internal fun buildArchiveNotificationPresentation(
  requestedCount: Int,
  archivedCount: Int,
  singleArchivedLabel: @NlsSafe String?,
  canUndo: Boolean,
): ArchiveNotificationPresentation {
  val title = AgentSessionsBundle.message(
    if (requestedCount == 1 && archivedCount == 1) {
      "toolwindow.notification.archive.title.single"
    }
    else {
      "toolwindow.notification.archive.title.multiple"
    }
  )
  val body = when {
    requestedCount == 1 && archivedCount == 1 -> {
      singleArchivedLabel?.takeIf(String::isNotBlank)
      ?: AgentSessionsBundle.message("toolwindow.notification.archive.body.single.generic")
    }

    archivedCount == requestedCount -> {
      AgentSessionsBundle.message("toolwindow.notification.archive.body.multiple", archivedCount)
    }

    else -> {
      AgentSessionsBundle.message("toolwindow.notification.archive.body.partial", archivedCount, requestedCount)
    }
  }
  return ArchiveNotificationPresentation(
    title = title,
    body = body,
    showUndoAction = canUndo,
  )
}
