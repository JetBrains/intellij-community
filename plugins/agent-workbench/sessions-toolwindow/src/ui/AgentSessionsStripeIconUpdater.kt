// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.toolwindow.ui

// @spec community/plugins/agent-workbench/spec/agent-sessions-tree.spec.md

import com.intellij.icons.AllIcons
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.IconManager
import com.intellij.util.SingleAlarm
import kotlinx.coroutines.CoroutineScope
import javax.swing.Icon

private const val AGENT_SESSIONS_TOOL_WINDOW_ID: String = "agent.workbench.sessions"

@Service(Service.Level.PROJECT)
internal class AgentSessionsStripeIconUpdater(
  private val project: Project,
  scope: CoroutineScope,
) {
  private val emptyIcon: Icon = AllIcons.Toolwindows.ToolWindowMessages
  private val attentionBadgedIcon: Icon by lazy {
    IconManager.getInstance().withIconBadge(emptyIcon, AgentSessionsStripeBadge.ATTENTION.color())
  }
  private val doneBadgedIcon: Icon by lazy {
    IconManager.getInstance().withIconBadge(emptyIcon, AgentSessionsStripeBadge.DONE.color())
  }

  private val alarm: SingleAlarm = SingleAlarm.singleEdtAlarm(
    delay = 50,
    coroutineScope = scope,
  ) {
    val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(AGENT_SESSIONS_TOOL_WINDOW_ID) ?: return@singleEdtAlarm
    val summary = project.service<AgentSessionsActivityService>().latestSummary()
    toolWindow.setIcon(
      when (summary.stripeBadge()) {
        AgentSessionsStripeBadge.ATTENTION -> attentionBadgedIcon
        AgentSessionsStripeBadge.DONE -> doneBadgedIcon
        null -> emptyIcon
      }
    )
  }

  fun scheduleUpdate() {
    alarm.cancelAndRequest()
  }
}
