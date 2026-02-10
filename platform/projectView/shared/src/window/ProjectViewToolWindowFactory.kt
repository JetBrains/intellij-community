// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.window

import com.intellij.icons.AllIcons
import com.intellij.ide.projectView.impl.ProjectViewImpl
import com.intellij.idea.AppMode
import com.intellij.openapi.components.serviceOrNull
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.util.PlatformUtils.isJetBrainsClient
import javax.swing.Icon

internal class ProjectViewToolWindowFactory : ToolWindowFactory, DumbAware {
  override val icon: Icon
    get() = AllIcons.Toolwindows.ToolWindowProject

  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    if (Registry.`is`("project.view.toolwindow.split", defaultValue = false)) {
      if (!isJetBrainsClient()) { // monolith or backend - to ensure that legacy panes work correctly
        legacyProjectView(project).setupBackend()
      }
      if (!AppMode.isRemoteDevHost()) { // monolith or frontend - the UI part
        ProjectViewToolWindowService.getInstance(project).setupToolWindow(toolWindow)
      }
    }
    else {
      legacyProjectView(project).setupImpl(toolWindow)
    }
  }

  override suspend fun manage(toolWindow: ToolWindow, toolWindowManager: ToolWindowManager) {
    if (Registry.`is`("project.view.toolwindow.split", defaultValue = false)) {
      // In the split tool window mode we only create the tool window on the frontend (or in the monolith), hence serviceOrNull.
      toolWindow.project.serviceOrNull<ProjectViewToolWindowService>()?.manageToolWindow(toolWindow)
    }
  }
}

private fun legacyProjectView(project: Project): ProjectViewImpl = ProjectViewImpl.getInstance(project) as ProjectViewImpl
