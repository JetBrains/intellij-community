// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.toolwindow.actions

import com.intellij.agent.workbench.sessions.core.settings.AgentWorkbenchSettings
import com.intellij.agent.workbench.sessions.core.statistics.AgentWorkbenchEntryPoint
import com.intellij.agent.workbench.sessions.frame.AgentWorkbenchDedicatedFrameProjectManager
import com.intellij.agent.workbench.sessions.service.normalizeOpenableSourceProjectPath
import com.intellij.agent.workbench.sessions.toolwindow.ui.AgentSessionsActivityBucket
import com.intellij.agent.workbench.sessions.toolwindow.ui.AgentSessionsActivityCounterAction
import com.intellij.agent.workbench.sessions.toolwindow.ui.AgentSessionsActivityService
import com.intellij.agent.workbench.sessions.toolwindow.ui.AgentSessionsActivitySummary
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.IdeFrame

internal class AgentSessionsMainToolbarActivityGroup @JvmOverloads constructor(
  private val isDedicatedProject: (Project) -> Boolean = AgentWorkbenchDedicatedFrameProjectManager::isDedicatedProject,
  private val sourceProjectPath: (Project) -> String? = { project -> normalizeOpenableSourceProjectPath(project.basePath) },
  private val isToolbarActivityEnabled: () -> Boolean = { AgentWorkbenchSettings.getInstance().showAgentActivityInMainToolbar },
  private val activitySummary: (Project) -> AgentSessionsActivitySummary = { project ->
    if (project.isInitialized) project.service<AgentSessionsActivityService>().latestMainToolbarSummary() else AgentSessionsActivitySummary.EMPTY
  },
  private val chromeActivitySummary: (Project) -> AgentSessionsActivitySummary = { project ->
    if (project.isInitialized) project.service<AgentSessionsActivityService>().latestChromeSummary() else AgentSessionsActivitySummary.EMPTY
  },
  private val projectProvider: (AnActionEvent) -> Project? = ::mainToolbarProject,
) : ActionGroup(), DumbAware {
  private val children: Array<AnAction> = AgentSessionsActivityBucket.entries.map { bucket ->
    AgentSessionsActivityCounterAction(
      bucket = bucket,
      rowsProvider = ::activityRowsFor,
      visibilityProvider = ::isVisibleForProject,
      projectProvider = projectProvider,
      entryPoint = AgentWorkbenchEntryPoint.TOOLBAR,
    )
  }.toTypedArray()

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = isVisibleForProject(projectProvider(e))
  }

  override fun getChildren(e: AnActionEvent?): Array<AnAction> = children

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  private fun isVisibleForProject(project: Project?): Boolean {
    if (project == null) return false
    if (isDedicatedProject(project)) return true
    return isToolbarActivityEnabled() &&
           sourceProjectPath(project) != null &&
           activitySummary(project).attentionRows.isNotEmpty()
  }

  private fun activityRowsFor(project: Project?, bucket: AgentSessionsActivityBucket) = when {
    project == null -> emptyList()
    isDedicatedProject(project) -> chromeActivitySummary(project).rowsFor(bucket)
    sourceProjectPath(project) == null -> emptyList()
    else -> activitySummary(project).rowsFor(bucket)
  }
}

private fun mainToolbarProject(e: AnActionEvent): Project? {
  return e.project ?: e.getData(IdeFrame.KEY)?.project
}
