// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.toolwindow.actions

import com.intellij.platform.ai.agent.core.session.AgentSessionProvider
import com.intellij.agent.workbench.sessions.AgentSessionsBundle
import com.intellij.agent.workbench.sessions.model.ArchiveThreadTarget
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction

internal abstract class AgentSessionsTreePopupArchiveStateAction(
  private val resolveContext: (AnActionEvent) -> AgentSessionsTreePopupActionContext?,
  private val resolveTargets: (AgentSessionsTreePopupActionContext) -> List<ArchiveThreadTarget>,
  private val canActOnProvider: (AgentSessionProvider) -> Boolean,
  private val singleTextKey: String,
  private val countTextKey: String,
) : DumbAwareAction() {
  override fun update(e: AnActionEvent) {
    val context = resolveContext(e)
    if (context == null) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    val targets = resolveTargets(context)
    val canAct = targets.any { target -> canActOnProvider(target.provider) }
    e.presentation.isEnabledAndVisible = canAct
    if (canAct) {
      e.presentation.text = if (targets.size > 1) {
        AgentSessionsBundle.message(countTextKey, targets.size)
      }
      else {
        AgentSessionsBundle.message(singleTextKey)
      }
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    val context = resolveContext(e) ?: return
    val targets = resolveTargets(context)
    if (targets.none { target -> canActOnProvider(target.provider) }) {
      return
    }
    performOnTargets(targets)
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  protected abstract fun performOnTargets(targets: List<ArchiveThreadTarget>)
}
