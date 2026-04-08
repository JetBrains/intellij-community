// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.actions

import com.intellij.agent.workbench.sessions.AgentSessionsBundle
import com.intellij.agent.workbench.sessions.core.statistics.AgentWorkbenchEntryPoint
import com.intellij.agent.workbench.sessions.frame.AgentWorkbenchDedicatedFrameProjectManager
import com.intellij.agent.workbench.sessions.service.AgentSessionLaunchService
import com.intellij.agent.workbench.sessions.service.normalizeOpenableSourceProjectPath
import com.intellij.agent.workbench.sessions.service.selectedChatSourceProjectPath
import com.intellij.ide.ui.ProductIcons
import com.intellij.ide.ui.laf.darcula.ui.ToolbarComboWidgetUiSizes
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.actionSystem.impl.ActionButtonWithText
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import javax.swing.JComponent

internal class AgentSessionsGoToSourceProjectFromToolbarAction : DumbAwareAction, CustomComponentAction {
  private val selectedSourcePath: (Project) -> String?
  private val isDedicatedProject: (Project) -> Boolean
  private val openProject: (String, AgentWorkbenchEntryPoint) -> Unit

  @Suppress("unused")
  constructor() {
    selectedSourcePath = ::selectedChatSourceProjectPath
    isDedicatedProject = AgentWorkbenchDedicatedFrameProjectManager::isDedicatedProject
    openProject = { path, entryPoint -> service<AgentSessionLaunchService>().openOrFocusProject(path, entryPoint) }
  }

  internal constructor(
    selectedSourcePath: (Project) -> String?,
    isDedicatedProject: (Project) -> Boolean,
    openProject: (String, AgentWorkbenchEntryPoint) -> Unit,
  ) {
    this.selectedSourcePath = selectedSourcePath
    this.isDedicatedProject = isDedicatedProject
    this.openProject = openProject
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    if (!isDedicatedProject(project)) {
      return
    }
    val sourceProjectPath = resolveSourceProjectPath(project) ?: return
    openProject(sourceProjectPath, AgentWorkbenchEntryPoint.TOOLBAR)
  }

  override fun update(e: AnActionEvent) {
    val project = e.project
    if (project == null || !isDedicatedProject(project)) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    val sourceProjectPath = resolveSourceProjectPath(project)
    e.presentation.isVisible = true
    e.presentation.icon = ProductIcons.getInstance().getProjectNodeIcon()
    if (sourceProjectPath == null) {
      e.presentation.isEnabled = false
      e.presentation.text = AgentSessionsBundle.message("action.AgentWorkbenchSessions.GoToSourceProjectFromToolbar.empty.text")
      e.presentation.description =
        AgentSessionsBundle.message("action.AgentWorkbenchSessions.GoToSourceProjectFromToolbar.empty.description")
      return
    }

    e.presentation.isEnabled = true
    e.presentation.text = sourceProjectName(sourceProjectPath)
    e.presentation.description =
      AgentSessionsBundle.message("action.AgentWorkbenchSessions.GoToSourceProjectFromToolbar.description", sourceProjectPath)
  }

  override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
    return object : ActionButtonWithText(this, presentation, place, ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE) {
      override fun iconTextSpace(): Int = ToolbarComboWidgetUiSizes.gapAfterLeftIcons
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  private fun resolveSourceProjectPath(project: Project): @NlsSafe String? {
    return normalizeOpenableSourceProjectPath(selectedSourcePath(project))
  }

  private fun sourceProjectName(path: @NlsSafe String): @NlsSafe String {
    return path.substringAfterLast('/').ifBlank { path }
  }
}
