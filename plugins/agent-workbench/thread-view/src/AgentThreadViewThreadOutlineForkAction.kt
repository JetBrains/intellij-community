// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.thread.view

import com.intellij.platform.ai.agent.sessions.core.launch.resolveAgentSessionThreadViewOpenPlan
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionThreadOutlineForkSource
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

internal class AgentThreadViewThreadOutlinePopupGroup : DefaultActionGroup(), DumbAware

internal class AgentThreadViewThreadOutlineForkAction : DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val target = resolveAgentThreadViewThreadOutlineTarget(e) ?: return
    e.coroutineScope.launch {
      forkAgentThreadViewThreadOutlineTarget(project = project, target = target)
    }
  }

  override fun update(e: AnActionEvent) {
    val target = resolveAgentThreadViewThreadOutlineTarget(e)
    val canFork = canForkAgentThreadViewThreadOutlineTarget(target)
    e.presentation.setEnabledAndVisible(canFork)
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}

private fun resolveAgentThreadViewThreadOutlineTarget(e: AnActionEvent): AgentThreadViewThreadOutlineTarget? {
  return AgentThreadViewThreadOutlineDataKeys.SELECTED_TARGET.getData(e.dataContext)
}

internal fun canShowAgentThreadViewThreadOutlineForkAction(target: AgentThreadViewThreadOutlineTarget?): Boolean {
  return canForkAgentThreadViewThreadOutlineTarget(target)
}

internal fun canForkAgentThreadViewThreadOutlineTarget(target: AgentThreadViewThreadOutlineTarget?): Boolean {
  target ?: return false
  val file = target.file
  val forkSource = target.source as? AgentSessionThreadOutlineForkSource ?: return false
  return file.provider != null &&
          !file.isPendingThread &&
          forkSource.canForkThreadFromOutlineItem(
            path = file.projectPath,
            threadId = file.threadId,
            itemId = target.item.id,
           subAgentId = file.subAgentId,
           tabKey = file.tabKey,
         )
}

internal suspend fun forkAgentThreadViewThreadOutlineTarget(
  project: Project,
  target: AgentThreadViewThreadOutlineTarget,
): Boolean {
  val file = target.file
  val provider = file.provider ?: return false
  if (!canForkAgentThreadViewThreadOutlineTarget(target)) {
    return false
  }
  val forkSource = target.source as? AgentSessionThreadOutlineForkSource ?: return false

  val forkResult = try {
    forkSource.forkThreadFromOutlineItem(
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
    resolveAgentSessionThreadViewOpenPlan(
      projectPath = file.projectPath,
      thread = forkedThread,
      subAgent = null,
      launchSpecOverride = forkResult.launchSpecOverride,
      project = project,
    )
  }
  catch (e: CancellationException) {
    throw e
  }
  catch (_: Exception) {
    return false
  }
  openThreadView(
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
  notifyAgentThreadViewScopedRefresh(
    provider = provider,
    projectPath = file.projectPath,
    threadId = forkedThread.id,
    activityReport = forkedThread.activityReport,
  )
  return true
}
