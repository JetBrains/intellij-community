// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.window

import com.intellij.icons.AllIcons
import com.intellij.ide.projectView.impl.ProjectViewImpl
import com.intellij.ide.projectView.impl.isProjectViewSplit
import com.intellij.idea.AppMode
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ProjectFrameCapabilitiesService
import com.intellij.openapi.wm.ex.ProjectFrameCapability
import com.intellij.util.PlatformUtils.isJetBrainsClient
import javax.swing.Icon

internal class ProjectViewToolWindowFactory : ToolWindowFactory, DumbAware {
  override val icon: Icon
    get() = AllIcons.Toolwindows.ToolWindowProject

  override suspend fun isApplicableAsync(project: Project): Boolean {
    return !serviceAsync<ProjectFrameCapabilitiesService>().has(project, ProjectFrameCapability.SUPPRESS_PROJECT_VIEW)
  }

  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    if (isProjectViewSplit()) {
      if (!AppMode.isRemoteDevHost()) { // monolith or frontend - the UI part
        ProjectViewToolWindowService.getInstance(project).setupToolWindow(toolWindow)
      }
    }
    else {
      legacyProjectView(project).setupImpl(toolWindow)
    }
  }

  override suspend fun manage(toolWindow: ToolWindow, toolWindowManager: ToolWindowManager) {
    if (isProjectViewSplit() && !AppMode.isRemoteDevHost()) { // monolith or frontend - the UI part
      ProjectViewToolWindowService.getInstance(toolWindow.project).manageToolWindow(toolWindow)
    }
  }
}

private fun legacyProjectView(project: Project): ProjectViewImpl = ProjectViewImpl.getInstance(project) as ProjectViewImpl
