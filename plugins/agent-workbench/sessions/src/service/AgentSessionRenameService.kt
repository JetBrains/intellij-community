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
import com.intellij.agent.workbench.sessions.core.providers.AgentThreadRenameHandler
import com.intellij.agent.workbench.sessions.core.statistics.AgentWorkbenchEntryPoint
import com.intellij.agent.workbench.sessions.util.SingleFlightActionGate
import com.intellij.agent.workbench.sessions.util.SingleFlightPolicy
import com.intellij.agent.workbench.sessions.util.buildAgentSessionIdentity
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
  private val dispatchRenameInEditorTab: suspend (
    AgentChatEditorTabActionContext,
    SessionActionTarget.Thread,
    AgentInitialMessageDispatchPlan,
  ) -> Unit,
  private val dispatchRenameFromTree: suspend (
    Project,
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
    dispatchRenameFromTree = ::dispatchRenameFromTreePopup,
    notifyRenameFailure = ::showRenameFailureNotification,
  )

  private val actionGate = SingleFlightActionGate()

  fun canRenameThreadInTree(target: SessionActionTarget.Thread): Boolean {
    val renameHandler = findProviderDescriptor(target.provider)?.threadRenameHandler ?: return false
    if (AgentThreadRenameContext.TREE_POPUP !in renameHandler.supportedContexts) {
      return false
    }
    return renameHandler !is AgentThreadRenameHandler.ChatDispatch || target.thread != null
  }

  fun canRenameThreadInEditorTab(context: AgentChatEditorTabActionContext, target: SessionActionTarget.Thread): Boolean {
    if (!matchesConcreteEditorThread(context, target)) {
      return false
    }
    val renameHandler = findProviderDescriptor(target.provider)?.threadRenameHandler ?: return false
    return AgentThreadRenameContext.EDITOR_TAB in renameHandler.supportedContexts
  }

  fun renameThreadFromTree(project: Project, target: SessionActionTarget.Thread, requestedName: String): Job? {
    return renameThread(
      project = project,
      target = target,
      requestedName = requestedName,
      context = null,
      renameContext = AgentThreadRenameContext.TREE_POPUP,
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
      project = null,
      target = target,
      requestedName = requestedName,
      context = context,
      renameContext = AgentThreadRenameContext.EDITOR_TAB,
    )
  }

  private fun renameThread(
    project: Project?,
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
    val renameHandler = descriptor?.threadRenameHandler
                          ?.takeIf { renameContext in it.supportedContexts } ?: return null

    return actionGate.launch(
      scope = serviceScope,
      key = buildRenameThreadActionKey(target.provider, target.threadId),
      policy = SingleFlightPolicy.DROP,
      onDrop = {
        LOG.debug("Dropped duplicate rename thread action for ${target.provider}:${target.threadId}")
      },
    ) {
      when (renameHandler) {
        is AgentThreadRenameHandler.Backend -> {
          val renamed = try {
            renameHandler.execute(
              path = target.path,
              threadId = target.threadId,
              normalizedName = normalizedRequestedName,
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

        is AgentThreadRenameHandler.ChatDispatch -> {
          val dispatchPlan = buildRenameDispatchPlan(
            handler = renameHandler,
            provider = target.provider,
            threadId = target.threadId,
            normalizedRequestedName = normalizedRequestedName,
          ) ?: return@launch
          try {
            when (renameContext) {
              AgentThreadRenameContext.EDITOR_TAB -> {
                val editorContext = context ?: run {
                  LOG.warn("Missing editor context for dispatch rename ${target.provider}:${target.threadId}")
                  notifyRenameFailure()
                  return@launch
                }
                dispatchRenameInEditorTab(editorContext, target, dispatchPlan)
              }

              AgentThreadRenameContext.TREE_POPUP -> {
                val currentProject = project ?: run {
                  LOG.warn("Missing project for tree rename ${target.provider}:${target.threadId}")
                  notifyRenameFailure()
                  return@launch
                }
                if (target.thread == null) {
                  LOG.warn("Missing thread model for tree dispatch rename ${target.provider}:${target.threadId}")
                  notifyRenameFailure()
                  return@launch
                }
                dispatchRenameFromTree(currentProject, target, dispatchPlan)
              }
            }
          }
          catch (t: Throwable) {
            if (t is CancellationException) {
              throw t
            }
            LOG.warn("Failed to dispatch rename for ${target.provider}:${target.threadId}", t)
            notifyRenameFailure()
            return@launch
          }

          refreshProviderForPath(target.path, target.provider)
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
  handler: AgentThreadRenameHandler.ChatDispatch,
  provider: AgentSessionProvider,
  threadId: String,
  normalizedRequestedName: String,
): AgentInitialMessageDispatchPlan? {
  val dispatchPlan = handler.buildDispatchPlan(normalizedRequestedName) ?: return null
  val dispatchSteps = dispatchPlan.postStartDispatchSteps.filter { step -> step.text.isNotBlank() }
  if (dispatchPlan.startupLaunchSpecOverride == null && dispatchSteps.isEmpty()) {
    return null
  }
  return dispatchPlan.copy(
    postStartDispatchSteps = dispatchSteps,
    initialMessageToken = dispatchPlan.initialMessageToken
                          ?: buildRenameThreadDispatchToken(provider, threadId, normalizedRequestedName),
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

private fun dispatchRenameFromTreePopup(
  project: Project,
  target: SessionActionTarget.Thread,
  dispatchPlan: AgentInitialMessageDispatchPlan,
) {
  service<AgentSessionLaunchService>().openChatThread(
    path = target.path,
    thread = checkNotNull(target.thread),
    entryPoint = AgentWorkbenchEntryPoint.TREE_POPUP,
    currentProject = project,
    initialMessageDispatchPlan = dispatchPlan,
  )
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
