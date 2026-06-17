// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.chat

import com.intellij.agent.workbench.common.buildAgentThreadIdentity
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

internal class AgentChatStructureViewForkAction : DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val target = resolveAgentChatStructureViewOutlineTarget(e) ?: return
    e.coroutineScope.launch {
      forkAgentChatStructureViewOutlineTarget(project = project, target = target)
    }
  }

  override fun update(e: AnActionEvent) {
    val target = resolveAgentChatStructureViewOutlineTarget(e)
    val canShow = canShowAgentChatStructureViewOutlineForkAction(target)
    e.presentation.isVisible = canShow
    e.presentation.isEnabled = canShow && canForkAgentChatStructureViewOutlineTarget(target)
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}

private fun resolveAgentChatStructureViewOutlineTarget(e: AnActionEvent): AgentChatStructureViewOutlineTarget? {
  (CommonDataKeys.NAVIGATABLE.getData(e.dataContext) as? AgentChatStructureViewOutlineElement)?.outlineTarget?.let { return it }
  return CommonDataKeys.NAVIGATABLE_ARRAY.getData(e.dataContext)
    ?.firstNotNullOfOrNull { navigatable -> (navigatable as? AgentChatStructureViewOutlineElement)?.outlineTarget }
}

internal fun canShowAgentChatStructureViewOutlineForkAction(target: AgentChatStructureViewOutlineTarget?): Boolean {
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
         )
}

internal fun canForkAgentChatStructureViewOutlineTarget(target: AgentChatStructureViewOutlineTarget?): Boolean {
  target ?: return false
  if (!canShowAgentChatStructureViewOutlineForkAction(target)) {
    return false
  }
  val file = target.file
  return target.source.canForkThreadFromOutlineItem(
    path = file.projectPath,
    threadId = file.threadId,
    itemId = target.item.id,
    subAgentId = file.subAgentId,
    tabKey = file.tabKey,
  )
}

internal suspend fun forkAgentChatStructureViewOutlineTarget(
  project: Project,
  target: AgentChatStructureViewOutlineTarget,
  rebindRequestedAtMs: Long = System.currentTimeMillis(),
): Boolean {
  val file = target.file
  val provider = file.provider ?: return false
  if (!canForkAgentChatStructureViewOutlineTarget(target)) {
    return false
  }
  if (!file.updateNewThreadRebindRequestedAtMs(rebindRequestedAtMs)) {
    return false
  }
  val tabsService = service<AgentChatTabsService>()
  tabsService.upsert(file.toSnapshot())

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
  catch (e: Throwable) {
    if (e is CancellationException) throw e
    null
  }
  val forkedThread = forkResult?.thread
  if (forkedThread == null) {
    clearAgentChatStructureViewForkAnchor(file = file, tabsService = tabsService, rebindRequestedAtMs = rebindRequestedAtMs)
    return false
  }

  val rebindReport = rebindOpenConcreteAgentChatTabs(
    provider = provider,
    requestsByProjectPath = mapOf(
      file.projectPath to listOf(
        AgentChatConcreteTabRebindRequest(
          tabKey = file.tabKey,
          currentThreadIdentity = file.threadIdentity,
          newThreadRebindRequestedAtMs = rebindRequestedAtMs,
          target = AgentChatTabRebindTarget(
            projectPath = file.projectPath,
            provider = forkedThread.provider,
            threadIdentity = buildAgentThreadIdentity(forkedThread.provider.value, forkedThread.id),
            threadId = forkedThread.id,
            threadTitle = forkedThread.title,
            threadActivity = forkedThread.activityReport.rowActivity,
            threadUpdatedAt = forkedThread.updatedAt,
          ),
        )
      )
    ),
  )
  if (rebindReport.reboundBindings <= 0) {
    clearAgentChatStructureViewForkAnchor(file = file, tabsService = tabsService, rebindRequestedAtMs = rebindRequestedAtMs)
    return false
  }

  tabsService.upsert(file.toSnapshot())
  notifyAgentChatScopedRefresh(
    provider = provider,
    projectPath = file.projectPath,
    threadId = forkedThread.id,
    activityReport = forkedThread.activityReport,
  )
  return true
}

private fun clearAgentChatStructureViewForkAnchor(
  file: AgentChatVirtualFile,
  tabsService: AgentChatTabsService,
  rebindRequestedAtMs: Long,
) {
  if (file.newThreadRebindRequestedAtMs != rebindRequestedAtMs) {
    return
  }
  if (file.updateNewThreadRebindRequestedAtMs(newThreadRebindRequestedAtMs = null)) {
    tabsService.upsert(file.toSnapshot())
  }
}
