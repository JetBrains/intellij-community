// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.service

import com.intellij.agent.workbench.chat.closeAndForgetAgentChatsForThread
import com.intellij.agent.workbench.common.normalizeAgentWorkbenchPath
import com.intellij.agent.workbench.sessions.AgentSessionsBundle
import com.intellij.agent.workbench.sessions.core.AgentSessionProvider
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderBehaviors
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderBridges
import com.intellij.agent.workbench.sessions.model.ArchiveThreadTarget
import com.intellij.agent.workbench.sessions.state.AgentSessionsStateStore
import com.intellij.agent.workbench.sessions.util.SingleFlightActionGate
import com.intellij.agent.workbench.sessions.util.SingleFlightPolicy
import com.intellij.agent.workbench.sessions.util.buildAgentSessionIdentity
import com.intellij.agent.workbench.sessions.util.isAgentSessionNewSessionId
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

private val LOG = logger<AgentSessionArchiveService>()

private const val ARCHIVE_THREADS_ACTION_KEY_PREFIX = "threads-archive"
private const val UNARCHIVE_THREADS_ACTION_KEY_PREFIX = "threads-unarchive"
private const val AGENT_SESSIONS_NOTIFICATION_GROUP_ID = "Agent Workbench Sessions"

@Service(Service.Level.APP)
internal class AgentSessionArchiveService(
  private val serviceScope: CoroutineScope,
  private val stateStore: AgentSessionsStateStore,
  private val syncService: AgentSessionRefreshService,
  private val archiveChatCleanup: suspend (projectPath: String, threadIdentity: String, subAgentId: String?) -> Unit,
) {
  @Suppress("unused")
  constructor(serviceScope: CoroutineScope) : this(
    serviceScope = serviceScope,
    stateStore = service<AgentSessionsStateStore>(),
    syncService = service<AgentSessionRefreshService>(),
    archiveChatCleanup = { projectPath, threadIdentity, subAgentId ->
      closeAndForgetAgentChatsForThread(projectPath = projectPath, threadIdentity = threadIdentity, subAgentId = subAgentId)
    },
  )

  private val actionGate = SingleFlightActionGate()

  fun canArchiveProvider(provider: AgentSessionProvider): Boolean {
    val bridge = AgentSessionProviderBridges.find(provider) ?: return false
    return bridge.supportsArchiveThread
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
      if (outcome.refreshDelayMs > 0L) {
        delay(outcome.refreshDelayMs.milliseconds)
      }
      syncService.refresh()
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
      var refreshDelayMs = 0L
      normalizedTargets.forEach { target ->
        val provider = target.provider
        val bridge = AgentSessionProviderBridges.find(provider)
        val behavior = AgentSessionProviderBehaviors.find(provider)
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
        if (behavior?.suppressArchivedThreadsDuringRefresh == true) {
          syncService.unsuppressArchivedTarget(target)
        }
        refreshDelayMs = maxOf(refreshDelayMs, behavior?.archiveRefreshDelayMs ?: 0L)
      }
      if (!anyUnarchived) {
        return@launchDropAction
      }
      if (refreshDelayMs > 0L) {
        delay(refreshDelayMs.milliseconds)
      }
      syncService.refresh()
    }
  }

  private suspend fun archiveTargetsInternal(targets: List<ArchiveThreadTarget>): ArchiveBatchOutcome {
    val archivedTargets = ArrayList<ArchiveThreadTarget>(targets.size)
    val undoTargets = ArrayList<ArchiveThreadTarget>()
    var refreshDelayMs = 0L

    targets.forEach { target ->
      val provider = target.provider
      val cleanupTarget = resolveArchivedChatCleanupTarget(
        path = target.path,
        provider = provider,
        archivedThreadId = target.threadId,
      )

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

      val bridge = AgentSessionProviderBridges.find(provider)
      if (bridge == null) {
        logMissingProviderBridge(provider)
        syncService.appendProviderUnavailableWarning(target.path, provider)
        return@forEach
      }
      if (!bridge.supportsArchiveThread) {
        return@forEach
      }
      val behavior = AgentSessionProviderBehaviors.find(provider)

      val archived = try {
        bridge.archiveThread(path = target.path, threadId = target.threadId)
      }
      catch (t: Throwable) {
        if (t is CancellationException) {
          throw t
        }
        LOG.warn("Failed to archive thread ${provider}:${target.threadId}", t)
        syncService.appendProviderUnavailableWarning(target.path, provider)
        return@forEach
      }

      if (!archived) {
        syncService.appendProviderUnavailableWarning(target.path, provider)
        return@forEach
      }

      if (behavior?.suppressArchivedThreadsDuringRefresh == true) {
        syncService.suppressArchivedTarget(target)
      }
      refreshDelayMs = maxOf(refreshDelayMs, behavior?.archiveRefreshDelayMs ?: 0L)
      contentRepository.removeArchivedTarget(target)

      try {
        archiveChatCleanup(target.path, cleanupTarget.threadIdentity, cleanupTarget.subAgentId)
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
      refreshDelayMs = refreshDelayMs,
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

  private fun resolveArchivedChatCleanupTarget(
    path: String,
    provider: AgentSessionProvider,
    archivedThreadId: String,
  ): ArchivedChatCleanupTarget {
    val parentThreadId = stateStore.findParentThreadIdForSubAgent(
      path = path,
      provider = provider,
      subAgentId = archivedThreadId,
    )
    if (parentThreadId == null) {
      return ArchivedChatCleanupTarget(
        threadIdentity = buildAgentSessionIdentity(provider, archivedThreadId),
        subAgentId = null,
      )
    }
    return ArchivedChatCleanupTarget(
      threadIdentity = buildAgentSessionIdentity(provider, parentThreadId),
      subAgentId = archivedThreadId,
    )
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
  @JvmField val archivedTargets: List<ArchiveThreadTarget>,
  @JvmField val undoTargets: List<ArchiveThreadTarget>,
  @JvmField val refreshDelayMs: Long,
)

private data class ArchivedChatCleanupTarget(
  @JvmField val threadIdentity: String,
  @JvmField val subAgentId: String?,
)

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
