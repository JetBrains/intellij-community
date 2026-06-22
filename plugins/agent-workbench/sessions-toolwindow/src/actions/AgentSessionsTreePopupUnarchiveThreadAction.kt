// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.toolwindow.actions

import com.intellij.platform.ai.agent.core.session.AgentSessionProvider
import com.intellij.agent.workbench.sessions.model.ArchiveThreadTarget
import com.intellij.agent.workbench.sessions.service.AgentSessionArchiveService
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service

internal class AgentSessionsTreePopupUnarchiveThreadAction : AgentSessionsTreePopupArchiveStateAction {
  private val unarchiveThreads: (List<ArchiveThreadTarget>) -> Unit

  @Suppress("unused")
  constructor() : this(
    resolveContext = ::resolveAgentSessionsTreePopupActionContext,
    canUnarchiveProvider = { provider -> service<AgentSessionArchiveService>().canUnarchiveProvider(provider) },
    unarchiveThreads = { targets -> service<AgentSessionArchiveService>().unarchiveThreads(targets) },
  )

  internal constructor(
    resolveContext: (AnActionEvent) -> AgentSessionsTreePopupActionContext?,
    canUnarchiveProvider: (AgentSessionProvider) -> Boolean,
    unarchiveThreads: (List<ArchiveThreadTarget>) -> Unit,
  ) : super(
    resolveContext = resolveContext,
    resolveTargets = { context -> context.unarchiveTargets },
    canActOnProvider = canUnarchiveProvider,
    singleTextKey = "toolwindow.action.unarchive",
    countTextKey = "toolwindow.action.unarchive.selected.count",
  ) {
    this.unarchiveThreads = unarchiveThreads
  }

  override fun performOnTargets(targets: List<ArchiveThreadTarget>) {
    unarchiveThreads(targets)
  }
}
