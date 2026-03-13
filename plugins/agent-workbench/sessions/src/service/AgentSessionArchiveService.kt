// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.service

// @spec community/plugins/agent-workbench/spec/agent-workbench-telemetry.spec.md

import com.intellij.agent.workbench.chat.closeAndForgetAgentChatsForThread
import com.intellij.agent.workbench.sessions.AgentSessionsBundle
import com.intellij.agent.workbench.sessions.core.AgentSessionProvider
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviders
import com.intellij.agent.workbench.sessions.core.statistics.AgentWorkbenchEntryPoint
import com.intellij.agent.workbench.sessions.core.statistics.AgentWorkbenchTelemetry
import com.intellij.agent.workbench.sessions.model.ArchiveThreadTarget
import com.intellij.agent.workbench.sessions.model.archiveThreadTargetKey
import com.intellij.agent.workbench.sessions.model.normalizeArchiveThreadTarget
import com.intellij.agent.workbench.sessions.state.AgentSessionWarmStateService
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
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
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
  )

  private val actionGate = SingleFlightActionGate()

  fun canArchiveProvider(provider: AgentSessionProvider): Boolean {
    val descriptor = AgentSessionProviders.find(provider) ?: return false
    return descriptor.supportsArchiveThread
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
      val outcome = archiveTargetsInternal(normalizedTargets, preferredSingleArchivedLabel)
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
    }
  }

  private suspend fun archiveTargetsInternal(
    targets: List<ArchiveThreadTarget>,
    preferredSingleArchivedLabel: @NlsSafe String?,
  ): ArchiveBatchOutcome {
    val singleArchivedLabel = preferredSingleArchivedLabel?.takeIf(String::isNotBlank)
                              ?: targets.singleOrNull()?.let(contentRepository::findArchiveNotificationLabel)
    val archivedTargets = ArrayList<ArchiveThreadTarget>(targets.size)
    val undoTargets = ArrayList<ArchiveThreadTarget>()
    var refreshDelayMs = 0L

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

      val archived = try {
        descriptor.archiveThread(path = target.path, threadId = target.threadId)
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

      if (descriptor.suppressArchivedThreadsDuringRefresh) {
        syncService.suppressArchivedTarget(target)
      }
      refreshDelayMs = maxOf(refreshDelayMs, descriptor.archiveRefreshDelayMs)
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
      if (descriptor.supportsUnarchiveThread) {
        undoTargets.add(target)
      }
    }

    return ArchiveBatchOutcome(
      requestedCount = targets.size,
      archivedTargets = archivedTargets,
      singleArchivedLabel = singleArchivedLabel,
      undoTargets = undoTargets,
      refreshDelayMs = refreshDelayMs,
    )
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
