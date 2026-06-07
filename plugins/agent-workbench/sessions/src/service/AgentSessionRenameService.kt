// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.service

import com.intellij.agent.workbench.chat.AgentChatEditorTabActionContext
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.sessions.AgentSessionsBundle
import com.intellij.agent.workbench.sessions.core.SessionActionTarget
import com.intellij.agent.workbench.sessions.core.normalizeAgentSessionTitle
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderDescriptor
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviders
import com.intellij.agent.workbench.sessions.state.AgentSessionThreadTitleOverrideStateService
import com.intellij.agent.workbench.sessions.state.AgentSessionThreadTitleOverrides
import com.intellij.agent.workbench.sessions.state.InMemoryAgentSessionThreadTitleOverrides
import com.intellij.agent.workbench.sessions.util.SingleFlightActionGate
import com.intellij.agent.workbench.sessions.util.SingleFlightPolicy
import com.intellij.agent.workbench.sessions.util.isAgentSessionNewSessionId
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.InputValidatorEx
import com.intellij.openapi.ui.Messages
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job

private val LOG = logger<AgentSessionRenameService>()

private const val RENAME_THREAD_ACTION_KEY_PREFIX = "thread-rename"
private const val AGENT_SESSIONS_NOTIFICATION_GROUP_ID = "Agent Workbench Sessions"

@Service(Service.Level.APP)
class AgentSessionRenameService internal constructor(
  private val serviceScope: CoroutineScope,
  private val refreshProviderForPath: (String, AgentSessionProvider) -> Unit,
  private val findProviderDescriptor: (AgentSessionProvider) -> AgentSessionProviderDescriptor?,
  private val notifyRenameFailure: () -> Unit,
  private val titleOverrides: AgentSessionThreadTitleOverrides = InMemoryAgentSessionThreadTitleOverrides(),
  private val threadPresentationUpdater: AgentSessionThreadPresentationUpdater = DefaultAgentSessionThreadPresentationUpdater(),
) {
  @Suppress("unused")
  constructor(serviceScope: CoroutineScope) : this(
    serviceScope = serviceScope,
    refreshProviderForPath = { path, provider -> service<AgentSessionRefreshService>().refreshProviderForPath(path, provider) },
    findProviderDescriptor = AgentSessionProviders::find,
    titleOverrides = service<AgentSessionThreadTitleOverrideStateService>(),
    notifyRenameFailure = ::showRenameFailureNotification,
  )

  private val actionGate = SingleFlightActionGate()

  fun canRenameThreadInTree(target: SessionActionTarget.Thread): Boolean {
    return !isAgentSessionNewSessionId(target.threadId) &&
           findProviderDescriptor(target.provider)?.threadRenameAction != null
  }

  fun canRenameThreadInEditorTab(context: AgentChatEditorTabActionContext, target: SessionActionTarget.Thread): Boolean {
    return matchesConcreteEditorThread(context, target) &&
           findProviderDescriptor(target.provider)?.threadRenameAction != null
  }

  fun renameThreadFromTree(target: SessionActionTarget.Thread, requestedName: String): Job? {
    return renameThread(
      target = target,
      requestedName = requestedName,
      context = null,
    )
  }

  fun renameThreadFromEditorTab(
    context: AgentChatEditorTabActionContext,
    target: SessionActionTarget.Thread,
    requestedName: String,
  ): Job? {
    if (!matchesConcreteEditorThread(context, target)) {
      return null
    }
    return renameThread(
      target = target,
      requestedName = requestedName,
      context = context,
    )
  }

  private fun renameThread(
    target: SessionActionTarget.Thread,
    requestedName: String,
    context: AgentChatEditorTabActionContext?,
  ): Job? {
    if (isAgentSessionNewSessionId(target.threadId)) {
      return null
    }
    val normalizedRequestedName = normalizeRenamedThreadTitle(requestedName) ?: return null
    val currentTitle = normalizeRenamedThreadTitle(target.title)
    if (normalizedRequestedName == currentTitle) {
      return null
    }

    val renameAction = findProviderDescriptor(target.provider)?.threadRenameAction ?: return null

    return actionGate.launch(
      scope = serviceScope,
      key = buildRenameThreadActionKey(target.provider, target.threadId),
      policy = SingleFlightPolicy.DROP,
      onDrop = {
        LOG.debug("Dropped duplicate rename thread action for ${target.provider}:${target.threadId}")
      },
    ) {
      val renamed = try {
        renameAction(
          target.path,
          target.threadId,
          normalizedRequestedName,
        )
      }
      catch (t: Throwable) {
        if (t is CancellationException) {
          throw t
        }
        LOG.warn("Failed to rename thread ${target.provider}:${target.threadId}", t)
        false
      }
      if (!renamed) {
        notifyRenameFailure()
        return@launch
      }

      recordTitleOverride(target = target, normalizedRequestedName = normalizedRequestedName)
      updateOpenTabPresentationAfterRename(
        target = target,
        normalizedRequestedName = normalizedRequestedName,
        context = context,
      )
      refreshProviderForPath(target.path, target.provider)
    }
  }

  private fun recordTitleOverride(target: SessionActionTarget.Thread, normalizedRequestedName: String) {
    titleOverrides.setTitle(
      path = target.path,
      provider = target.provider,
      threadId = target.threadId,
      title = normalizedRequestedName,
    )
  }

  private suspend fun updateOpenTabPresentationAfterRename(
    target: SessionActionTarget.Thread,
    normalizedRequestedName: String,
    context: AgentChatEditorTabActionContext?,
  ) {
    val activity = context?.threadActivity ?: target.thread?.activity
    threadPresentationUpdater.updateThread(
      provider = target.provider,
      path = target.path,
      threadId = target.threadId,
      title = normalizedRequestedName,
      activity = activity,
    )
  }
}

private fun matchesConcreteEditorThread(
  context: AgentChatEditorTabActionContext,
  target: SessionActionTarget.Thread,
): Boolean {
  val threadCoordinates = context.threadCoordinates ?: return false
  return context.path == target.path &&
         !threadCoordinates.isPending &&
         threadCoordinates.provider == target.provider &&
         threadCoordinates.sessionId == target.threadId
}

fun showRenameThreadDialog(project: Project, currentTitle: String): String? {
  return Messages.showMultilineInputDialog(
    project,
    AgentSessionsBundle.message("toolwindow.rename.dialog.message"),
    AgentSessionsBundle.message("toolwindow.rename.dialog.title"),
    currentTitle,
    Messages.getQuestionIcon(),
    object : InputValidatorEx {
      override fun checkInput(inputString: String?): Boolean {
        return normalizeRenamedThreadTitle(inputString) != null
      }

      override fun canClose(inputString: String?): Boolean = checkInput(inputString)

      override fun getErrorText(inputString: String?): String? {
        return if (normalizeRenamedThreadTitle(inputString) == null) {
          AgentSessionsBundle.message("toolwindow.rename.dialog.validation.empty")
        }
        else {
          null
        }
      }
    },
  )
}

internal fun normalizeRenamedThreadTitle(value: String?): String? {
  return normalizeAgentSessionTitle(value)
}

private fun buildRenameThreadActionKey(provider: AgentSessionProvider, threadId: String): String {
  return "$RENAME_THREAD_ACTION_KEY_PREFIX:${provider.value}:$threadId"
}

private fun showRenameFailureNotification() {
  if (ApplicationManager.getApplication().isUnitTestMode) {
    return
  }

  runCatching {
    NotificationGroupManager.getInstance()
      .getNotificationGroup(AGENT_SESSIONS_NOTIFICATION_GROUP_ID)
      .createNotification(
        AgentSessionsBundle.message("toolwindow.notification.rename.failure.title"),
        AgentSessionsBundle.message("toolwindow.notification.rename.failure.body"),
        NotificationType.ERROR,
      )
      .notify(null)
  }.onFailure { error ->
    LOG.warn("Failed to show rename thread failure notification", error)
  }
}
