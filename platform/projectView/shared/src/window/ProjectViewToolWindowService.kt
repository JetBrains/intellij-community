// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.window

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.platform.projectView.actions.ProjectViewActionSupport
import com.intellij.platform.projectView.pane.ProjectViewNodePath
import com.intellij.platform.projectView.pane.ProjectViewPaneDescriptorImpl
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface ProjectViewToolWindowService {
  companion object {
    @JvmStatic fun getInstance(project: Project): ProjectViewToolWindowService = project.service()
  }

  val currentPaneDescriptor: ProjectViewPaneDescriptorImpl?

  @RequiresEdt
  fun setupToolWindow(toolWindow: ToolWindow)

  suspend fun manageToolWindow(toolWindow: ToolWindow)

  fun getActionSupport(): ProjectViewActionSupport

  suspend fun show(requestFocus: Boolean)

  suspend fun selectNode(nodePath: ProjectViewNodePath)
}
