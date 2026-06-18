// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.chat

import com.intellij.agent.workbench.sessions.core.launch.resolveAgentSessionChatOpenPlan
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

internal class AgentChatThreadOutlinePopupGroup : DefaultActionGroup(), DumbAware

internal class AgentChatThreadOutlineForkAction : DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val target = resolveAgentChatThreadOutlineTarget(e) ?: return
    e.coroutineScope.launch {
      forkAgentChatThreadOutlineTarget(project = project, target = target)
    }
  }

  override fun update(e: AnActionEvent) {
    val target = resolveAgentChatThreadOutlineTarget(e)
    val canFork = canForkAgentChatThreadOutlineTarget(target)
    e.presentation.setEnabledAndVisible(canFork)
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}

private fun resolveAgentChatThreadOutlineTarget(e: AnActionEvent): AgentChatThreadOutlineTarget? {
  return AgentChatThreadOutlineDataKeys.SELECTED_TARGET.getData(e.dataContext)
}

internal fun canShowAgentChatThreadOutlineForkAction(target: AgentChatThreadOutlineTarget?): Boolean {
  return canForkAgentChatThreadOutlineTarget(target)
}

internal fun canForkAgentChatThreadOutlineTarget(target: AgentChatThreadOutlineTarget?): Boolean {
  target ?: return false
  val file = target.file
  return file.provider != null &&
         !file.isPendingThread &&
         target.source.canShowThreadOutlineForkAction(
           path = file.projectPath,
           threadId = file.threadId,
           itemId = target.item.id,
           subAgentId = file.subAgentId,
           tabKey = file.tabKey,
         ) &&
         target.source.canForkThreadFromOutlineItem(
           path = file.projectPath,
           threadId = file.threadId,
           itemId = target.item.id,
           subAgentId = file.subAgentId,
           tabKey = file.tabKey,
         )
}

internal suspend fun forkAgentChatThreadOutlineTarget(
  project: Project,
  target: AgentChatThreadOutlineTarget,
): Boolean {
  val file = target.file
  val provider = file.provider ?: return false
  if (!canForkAgentChatThreadOutlineTarget(target)) {
    return false
  }

  val forkResult = try {
    target.source.forkThreadFromOutlineItem(
      project = project,
      path = file.projectPath,
      threadId = file.threadId,
      itemId = target.item.id,
      subAgentId = file.subAgentId,
      tabKey = file.tabKey,
    )
  }
  catch (e: CancellationException) {
    throw e
  }
  catch (_: Exception) {
    null
  }
  val forkedThread = forkResult?.thread
  if (forkedThread == null) {
    return false
  }

  val openPlan = try {
    resolveAgentSessionChatOpenPlan(
      projectPath = file.projectPath,
      thread = forkedThread,
      subAgent = null,
      launchSpecOverride = null,
      project = project,
    )
  }
  catch (e: CancellationException) {
    throw e
  }
  catch (_: Exception) {
    return false
  }
  openChat(
    project = project,
    projectPath = file.projectPath,
    threadIdentity = openPlan.threadIdentity,
    shellCommand = openPlan.launchSpec.command,
    shellEnvVariables = openPlan.launchSpec.envVariables,
    threadId = openPlan.runtimeThreadId,
    threadTitle = openPlan.threadTitle,
    subAgentId = openPlan.subAgentId,
    threadActivity = forkedThread.activityReport.rowActivity,
    initialMessageDispatchPlan = openPlan.initialMessageDispatchPlan,
    startupLaunchSpec = openPlan.launchSpec,
  )
  notifyAgentChatScopedRefresh(
    provider = provider,
    projectPath = file.projectPath,
    threadId = forkedThread.id,
    activityReport = forkedThread.activityReport,
  )
  return true
}
