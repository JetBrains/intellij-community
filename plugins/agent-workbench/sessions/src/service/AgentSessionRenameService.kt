// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.service

import com.intellij.agent.workbench.chat.AgentChatEditorTabActionContext
import com.intellij.agent.workbench.common.AgentThreadActivityReport
import com.intellij.agent.workbench.common.normalizeAgentWorkbenchPath
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.common.session.AgentSessionThread
import com.intellij.agent.workbench.sessions.AgentSessionsBundle
import com.intellij.agent.workbench.sessions.core.SessionActionTarget
import com.intellij.agent.workbench.sessions.core.AgentSessionThreadPresentationModel
import com.intellij.agent.workbench.sessions.core.normalizeAgentSessionTitle
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderDescriptor
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviders
import com.intellij.agent.workbench.sessions.model.AgentProjectSessions
import com.intellij.agent.workbench.sessions.model.AgentWorktree
import com.intellij.agent.workbench.sessions.state.AgentSessionsStateStore
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
  private val stateStore: AgentSessionsStateStore = service<AgentSessionsStateStore>(),
  private val presentationModel: AgentSessionThreadPresentationModel = service<AgentSessionThreadPresentationModel>(),
) {
  @Suppress("unused")
  constructor(serviceScope: CoroutineScope) : this(
    serviceScope = serviceScope,
    refreshProviderForPath = { path, provider -> service<AgentSessionRefreshService>().refreshProviderForPath(path, provider) },
    findProviderDescriptor = AgentSessionProviders::find,
    notifyRenameFailure = ::showRenameFailureNotification,
    stateStore = service<AgentSessionsStateStore>(),
    presentationModel = service<AgentSessionThreadPresentationModel>(),
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

      updateStateAfterRename(target = target, normalizedRequestedName = normalizedRequestedName)
      updatePresentationAfterRename(
        target = target,
        normalizedRequestedName = normalizedRequestedName,
        context = context,
      )
      refreshProviderForPath(target.path, target.provider)
    }
  }

  private fun updateStateAfterRename(target: SessionActionTarget.Thread, normalizedRequestedName: String) {
    val normalizedPath = normalizeAgentWorkbenchPath(target.path)
    stateStore.update { state ->
      var changed = false
      val nextProjects = state.projects.map { project ->
        val updatedProject = project.withRenamedThread(
          normalizedPath = normalizedPath,
          provider = target.provider,
          threadId = target.threadId,
          title = normalizedRequestedName,
        )
        if (updatedProject != project) {
          changed = true
        }
        updatedProject
      }
      if (!changed) {
        state
      }
      else {
        state.copy(projects = nextProjects, lastUpdatedAt = System.currentTimeMillis())
      }
    }
  }

  private fun updatePresentationAfterRename(
    target: SessionActionTarget.Thread,
    normalizedRequestedName: String,
    context: AgentChatEditorTabActionContext?,
  ) {
    val activityReport = target.thread?.activityReport ?: context?.threadActivity?.let(::AgentThreadActivityReport)
    presentationModel.updateThread(
      path = target.path,
      provider = target.provider,
      threadId = target.threadId,
      title = normalizedRequestedName,
      activity = null,
      activityReport = activityReport,
    )
  }
}

private fun AgentProjectSessions.withRenamedThread(
  normalizedPath: String,
  provider: AgentSessionProvider,
  threadId: String,
  title: String,
): AgentProjectSessions {
  var updatedProject = this
  if (normalizeAgentWorkbenchPath(path) == normalizedPath) {
    val updatedThreads = threads.withRenamedThread(provider = provider, threadId = threadId, title = title)
    if (updatedThreads != threads) {
      updatedProject = updatedProject.copy(threads = updatedThreads)
    }
  }

  val updatedWorktrees = worktrees.map { worktree ->
    if (normalizeAgentWorkbenchPath(worktree.path) == normalizedPath) {
      worktree.withRenamedThread(provider = provider, threadId = threadId, title = title)
    }
    else {
      worktree
    }
  }
  return if (updatedWorktrees == worktrees) updatedProject else updatedProject.copy(worktrees = updatedWorktrees)
}

private fun AgentWorktree.withRenamedThread(
  provider: AgentSessionProvider,
  threadId: String,
  title: String,
): AgentWorktree {
  val updatedThreads = threads.withRenamedThread(provider = provider, threadId = threadId, title = title)
  return if (updatedThreads == threads) this else copy(threads = updatedThreads)
}

private fun List<AgentSessionThread>.withRenamedThread(
  provider: AgentSessionProvider,
  threadId: String,
  title: String,
): List<AgentSessionThread> {
  var changed = false
  val updatedThreads = map { thread ->
    if (thread.provider == provider && thread.id == threadId && thread.title != title) {
      changed = true
      thread.copy(title = title)
    }
    else {
      thread
    }
  }
  return if (changed) updatedThreads else this
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
