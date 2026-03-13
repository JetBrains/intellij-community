// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.actions

import com.intellij.agent.workbench.chat.AgentChatEditorTabActionContext
import com.intellij.agent.workbench.chat.resolveAgentChatEditorTabActionContext
import com.intellij.agent.workbench.sessions.core.AgentSessionProvider
import com.intellij.agent.workbench.sessions.core.statistics.AgentWorkbenchEntryPoint
import com.intellij.agent.workbench.sessions.model.ArchiveThreadTarget
import com.intellij.agent.workbench.sessions.service.AgentSessionArchiveService
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service

internal class AgentSessionsEditorTabArchiveThreadAction @JvmOverloads constructor(
  private val canArchiveProvider: (AgentSessionProvider) -> Boolean = { provider ->
    service<AgentSessionArchiveService>().canArchiveProvider(provider)
  },
  private val archiveThreads: (List<ArchiveThreadTarget>, String?, AgentWorkbenchEntryPoint) -> Unit = { targets, preferredSingleArchivedLabel, entryPoint ->
    service<AgentSessionArchiveService>().archiveThreads(targets, entryPoint, preferredSingleArchivedLabel)
  },
  resolveContext: (AnActionEvent) -> AgentChatEditorTabActionContext? = ::resolveAgentChatEditorTabActionContext,
) : AgentSessionsEditorTabActionBase(resolveContext) {

  override fun update(e: AnActionEvent) {
    val context = resolveEditorTabContextOrHide(e) ?: return

    val archiveTarget = resolveArchiveThreadTargetFromEditorTabContext(context)
    e.presentation.text = templatePresentation.textWithMnemonic
    e.presentation.isVisible = true
    e.presentation.isEnabled = archiveTarget != null && canArchiveProvider(archiveTarget.provider)
  }

  override fun actionPerformed(e: AnActionEvent) {
    val context = resolveEditorTabContext(e) ?: return
    val archiveTarget = resolveArchiveThreadTargetFromEditorTabContext(context) ?: return
    val preferredSingleArchivedLabel = resolvePreferredArchiveNotificationLabelFromEditorTabContext(context)
    if (!canArchiveProvider(archiveTarget.provider)) {
      return
    }
    archiveThreads(listOf(archiveTarget), preferredSingleArchivedLabel, AgentWorkbenchEntryPoint.EDITOR_TAB_POPUP)
  }
}
