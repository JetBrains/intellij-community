// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.service

import com.intellij.agent.workbench.chat.AgentChatEditorTabActionContext
import com.intellij.agent.workbench.chat.openChat
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.sessions.AgentSessionsBundle
import com.intellij.agent.workbench.sessions.core.SessionActionTarget
import com.intellij.agent.workbench.sessions.core.launch.AgentSessionLaunchSpecs
import com.intellij.agent.workbench.sessions.core.normalizeAgentSessionTitle
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessageDispatchPlan
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderDescriptor
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviders
import com.intellij.agent.workbench.sessions.core.providers.AgentThreadRenameContext
import com.intellij.agent.workbench.sessions.core.providers.AgentThreadRenameMode
import com.intellij.agent.workbench.sessions.util.SingleFlightActionGate
import com.intellij.agent.workbench.sessions.util.SingleFlightPolicy
import com.intellij.agent.workbench.sessions.util.buildAgentSessionIdentity
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.InputValidatorEx
import com.intellij.openapi.ui.Messages
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext

private val LOG = logger<AgentSessionRenameService>()

private const val RENAME_THREAD_ACTION_KEY_PREFIX = "thread-rename"
private const val AGENT_SESSIONS_NOTIFICATION_GROUP_ID = "Agent Workbench Sessions"

@Service(Service.Level.APP)
class AgentSessionRenameService internal constructor(
  private val serviceScope: CoroutineScope,
  private val refreshProviderForPath: (String, AgentSessionProvider) -> Unit,
  private val findProviderDescriptor: (AgentSessionProvider) -> AgentSessionProviderDescriptor?,
  private val dispatchRenameInEditorTab: suspend (
    AgentChatEditorTabActionContext,
    SessionActionTarget.Thread,
    AgentInitialMessageDispatchPlan,
  ) -> Unit,
  private val notifyRenameFailure: () -> Unit,
) {
  @Suppress("unused")
  constructor(serviceScope: CoroutineScope) : this(
    serviceScope = serviceScope,
    refreshProviderForPath = { path, provider -> service<AgentSessionRefreshService>().refreshProviderForPath(path, provider) },
    findProviderDescriptor = AgentSessionProviders::find,
    dispatchRenameInEditorTab = ::dispatchRenameToOpenEditorTab,
    notifyRenameFailure = ::showRenameFailureNotification,
  )

  private val actionGate = SingleFlightActionGate()

  fun canRenameThreadInTree(target: SessionActionTarget.Thread): Boolean {
    return findProviderDescriptor(target.provider)
      ?.renameThreadMode(AgentThreadRenameContext.TREE_POPUP) == AgentThreadRenameMode.BACKEND
  }

  fun canRenameThreadInEditorTab(context: AgentChatEditorTabActionContext, target: SessionActionTarget.Thread): Boolean {
    if (!matchesConcreteEditorThread(context, target)) {
      return false
    }
    return findProviderDescriptor(target.provider)
      ?.renameThreadMode(AgentThreadRenameContext.EDITOR_TAB) != null
  }

  fun renameThreadFromTree(target: SessionActionTarget.Thread, requestedName: String): Job? {
    return renameThread(target = target, requestedName = requestedName, context = null, renameContext = AgentThreadRenameContext.TREE_POPUP)
  }

  fun renameThreadFromEditorTab(
    context: AgentChatEditorTabActionContext,
    target: SessionActionTarget.Thread,
    requestedName: String,
  ): Job? {
    if (!matchesConcreteEditorThread(context, target)) {
      return null
    }
    return renameThread(target = target, requestedName = requestedName, context = context, renameContext = AgentThreadRenameContext.EDITOR_TAB)
  }

  private fun renameThread(
    target: SessionActionTarget.Thread,
    requestedName: String,
    context: AgentChatEditorTabActionContext?,
    renameContext: AgentThreadRenameContext,
  ): Job? {
    val normalizedRequestedName = normalizeRenamedThreadTitle(requestedName) ?: return null
    val currentTitle = normalizeRenamedThreadTitle(target.title)
    if (normalizedRequestedName == currentTitle) {
      return null
    }

    val descriptor = findProviderDescriptor(target.provider)
    val renameMode = descriptor?.renameThreadMode(renameContext) ?: return null
    if (renameMode == AgentThreadRenameMode.ACTIVE_EDITOR_DISPATCH && context == null) {
      return null
    }

    return actionGate.launch(
      scope = serviceScope,
      key = buildRenameThreadActionKey(target.provider, target.threadId),
      policy = SingleFlightPolicy.DROP,
      onDrop = {
        LOG.debug("Dropped duplicate rename thread action for ${target.provider}:${target.threadId}")
      },
    ) {
      when (renameMode) {
        AgentThreadRenameMode.BACKEND -> {
          val renamed = try {
            descriptor.renameThread(
              path = target.path,
              threadId = target.threadId,
              name = normalizedRequestedName,
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

          refreshProviderForPath(target.path, target.provider)
        }

        AgentThreadRenameMode.ACTIVE_EDITOR_DISPATCH -> {
          val dispatchPlan = buildRenameDispatchPlan(
            descriptor = descriptor,
            provider = target.provider,
            threadId = target.threadId,
            normalizedRequestedName = normalizedRequestedName,
          ) ?: return@launch
          try {
            dispatchRenameInEditorTab(checkNotNull(context), target, dispatchPlan)
          }
          catch (t: Throwable) {
            if (t is CancellationException) {
              throw t
            }
            LOG.warn("Failed to dispatch rename for ${target.provider}:${target.threadId}", t)
            notifyRenameFailure()
          }
        }
      }
    }
  }
}

private fun matchesConcreteEditorThread(
  context: AgentChatEditorTabActionContext,
  target: SessionActionTarget.Thread,
): Boolean {
  val threadCoordinates = context.threadCoordinates ?: return false
  if (threadCoordinates.isPending) {
    return false
  }
  return context.path == target.path &&
         threadCoordinates.provider == target.provider &&
         threadCoordinates.sessionId == target.threadId
}

private fun buildRenameDispatchPlan(
  descriptor: AgentSessionProviderDescriptor,
  provider: AgentSessionProvider,
  threadId: String,
  normalizedRequestedName: String,
): AgentInitialMessageDispatchPlan? {
  val dispatchSteps = descriptor.buildRenameThreadDispatchSteps(normalizedRequestedName)
    .filter { step -> step.text.isNotBlank() }
  if (dispatchSteps.isEmpty()) {
    return null
  }
  return AgentInitialMessageDispatchPlan(
    postStartDispatchSteps = dispatchSteps,
    initialMessageToken = buildRenameThreadDispatchToken(provider, threadId, normalizedRequestedName),
  )
}

private fun buildRenameThreadDispatchToken(provider: AgentSessionProvider, threadId: String, normalizedRequestedName: String): String {
  return "rename:${provider.value}:$threadId:${normalizedRequestedName.hashCode()}:${System.nanoTime()}"
}

private suspend fun dispatchRenameToOpenEditorTab(
  context: AgentChatEditorTabActionContext,
  target: SessionActionTarget.Thread,
  dispatchPlan: AgentInitialMessageDispatchPlan,
) {
  val launchSpec = AgentSessionLaunchSpecs.resolveResume(
    projectPath = target.path,
    provider = target.provider,
    sessionId = target.threadId,
  )
  withContext(Dispatchers.EDT) {
    openChat(
      project = context.project,
      projectPath = target.path,
      threadIdentity = buildAgentSessionIdentity(provider = target.provider, sessionId = target.threadId),
      shellCommand = launchSpec.command,
      shellEnvVariables = launchSpec.envVariables,
      threadId = target.threadId,
      threadTitle = target.title,
      subAgentId = null,
      threadActivity = context.threadActivity,
      initialMessageDispatchPlan = dispatchPlan,
    )
  }
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
