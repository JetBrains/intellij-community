// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.service

import com.intellij.agent.workbench.chat.AgentChatFocusService
import com.intellij.agent.workbench.sessions.core.statistics.AgentWorkbenchEntryPoint
import com.intellij.agent.workbench.sessions.core.statistics.AgentWorkbenchTelemetry
import com.intellij.agent.workbench.sessions.frame.AgentWorkbenchDedicatedFrameProjectManager
import com.intellij.ide.impl.ProjectUtilService
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object AgentSourceChatSwitching {
  fun selectedOpenableSourceProjectPath(project: Project): String? {
    return normalizeOpenableSourceProjectPath(selectedChatSourceProjectPath(project))
  }

  fun switchSourceAndChat(project: Project, entryPoint: AgentWorkbenchEntryPoint): Boolean {
    return if (AgentWorkbenchDedicatedFrameProjectManager.isDedicatedProject(project)) {
      focusSourceProjectFromDedicatedFrame(project, entryPoint)
    }
    else {
      focusActiveChatFromSourceProject(project, entryPoint)
    }
  }

  fun focusActiveChatFromSourceProject(project: Project, entryPoint: AgentWorkbenchEntryPoint): Boolean {
    val sourceProjectPath = normalizeOpenableSourceProjectPath(project.basePath)
    val dedicatedProject = openDedicatedFrameProject()
    if (dedicatedProject != null) {
      if (sourceProjectPath != null && dedicatedProject.service<AgentChatFocusService>().focusRecentOrFirstChatTab(sourceProjectPath)) {
        dedicatedProject.service<ProjectUtilService>().focusProjectWindow()
        AgentWorkbenchTelemetry.logDedicatedFrameFocusRequested(entryPoint)
        return true
      }

      dedicatedProject.service<ProjectUtilService>().focusProjectWindow()
      AgentWorkbenchTelemetry.logDedicatedFrameFocusRequested(entryPoint)
      return false
    }

    service<AgentSessionLaunchService>().openOrFocusDedicatedFrame(entryPoint, project)
    return false
  }

  fun focusSourceProjectFromDedicatedFrame(project: Project, entryPoint: AgentWorkbenchEntryPoint): Boolean {
    val sourceProjectPath = selectedOpenableSourceProjectPath(project) ?: return false
    service<AgentSessionLaunchService>().openOrFocusProject(sourceProjectPath, entryPoint)
    return true
  }

  private fun openDedicatedFrameProject(): Project? {
    return ProjectManager.getInstance().openProjects.firstOrNull { project ->
      !project.isDisposed && AgentWorkbenchDedicatedFrameProjectManager.isDedicatedProject(project)
    }
  }
}
