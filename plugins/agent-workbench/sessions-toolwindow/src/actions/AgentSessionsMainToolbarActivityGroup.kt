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
import com.intellij.agent.workbench.sessions.toolwindow.ui.AgentSessionsActivityThreadRow
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.IdeFrame

private val LOG = logger<AgentSessionsMainToolbarActivityGroup>()

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
    val project = projectProvider(e)
    val visible = isVisibleForProject(project)
    e.presentation.isEnabledAndVisible = visible
    LOG.debug { "Main toolbar activity group update project=${project?.name} visible=$visible" }
  }

  override fun getChildren(e: AnActionEvent?): Array<AnAction> = children

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  private fun isVisibleForProject(project: Project?): Boolean {
    if (project == null) {
      LOG.debug { "Main toolbar activity visibility project=null visible=false" }
      return false
    }
    val dedicated = isDedicatedProject(project)
    if (dedicated) {
      LOG.debug { "Main toolbar activity visibility project=${project.name} dedicated=true visible=true" }
      return true
    }
    val toolbarEnabled = isToolbarActivityEnabled()
    val sourcePath = if (toolbarEnabled) sourceProjectPath(project) else null
    val summary = if (sourcePath == null) AgentSessionsActivitySummary.EMPTY else activitySummary(project)
    val visible = toolbarEnabled && sourcePath != null && summary.attentionRows.isNotEmpty()
    LOG.debug {
      "Main toolbar activity visibility project=${project.name} dedicated=false " +
      "toolbarEnabled=$toolbarEnabled sourcePath=$sourcePath summary=${summary.countsDebugText()} visible=$visible"
    }
    return visible
  }

  private fun activityRowsFor(project: Project?, bucket: AgentSessionsActivityBucket): List<AgentSessionsActivityThreadRow> {
    if (project == null) {
      LOG.debug { "Main toolbar activity rows project=null bucket=$bucket count=0" }
      return emptyList()
    }
    val dedicated = isDedicatedProject(project)
    val sourcePath = if (dedicated) null else sourceProjectPath(project)
    val rows = when {
      dedicated -> chromeActivitySummary(project).rowsFor(bucket)
      sourcePath == null -> emptyList()
      else -> activitySummary(project).rowsFor(bucket)
    }
    LOG.debug {
      "Main toolbar activity rows project=${project.name} dedicated=$dedicated sourcePath=$sourcePath bucket=$bucket count=${rows.size}"
    }
    return rows
  }
}

private fun AgentSessionsActivitySummary.countsDebugText(): String {
  return "attention=${attentionRows.size},running=${runningRows.size},done=${doneRows.size}"
}

private fun mainToolbarProject(e: AnActionEvent): Project? {
  return e.project ?: e.getData(IdeFrame.KEY)?.project
}
