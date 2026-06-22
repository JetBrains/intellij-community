// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.toolwindow.actions

import com.intellij.platform.ai.agent.core.session.AgentSessionProvider
import com.intellij.agent.workbench.sessions.statistics.AgentWorkbenchEntryPoint
import com.intellij.agent.workbench.sessions.model.ArchiveThreadTarget
import com.intellij.agent.workbench.sessions.service.AgentSessionArchiveService
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service

internal class AgentSessionsTreePopupArchiveThreadAction : AgentSessionsTreePopupArchiveStateAction {
  private val archiveThreads: (List<ArchiveThreadTarget>, AgentWorkbenchEntryPoint) -> Unit

  @Suppress("unused")
  constructor() : this(
    resolveContext = ::resolveAgentSessionsTreePopupActionContext,
    canArchiveProvider = { provider -> service<AgentSessionArchiveService>().canArchiveProvider(provider) },
    archiveThreads = { targets, entryPoint -> service<AgentSessionArchiveService>().archiveThreads(targets, entryPoint) },
  )

  internal constructor(
    resolveContext: (AnActionEvent) -> AgentSessionsTreePopupActionContext?,
    canArchiveProvider: (AgentSessionProvider) -> Boolean,
    archiveThreads: (List<ArchiveThreadTarget>, AgentWorkbenchEntryPoint) -> Unit,
  ) : super(
    resolveContext = resolveContext,
    resolveTargets = { context -> context.archiveTargets },
    canActOnProvider = canArchiveProvider,
    singleTextKey = "toolwindow.action.archive",
    countTextKey = "toolwindow.action.archive.selected.count",
  ) {
    this.archiveThreads = archiveThreads
  }

  override fun performOnTargets(targets: List<ArchiveThreadTarget>) {
    archiveThreads(targets, AgentWorkbenchEntryPoint.TREE_POPUP)
  }
}
