// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsListener
import com.intellij.openapi.wm.ToolWindow

internal class ChangesViewToolWindowManagerImpl(private val project: Project) : ChangesViewToolWindowManager {
  override fun setToolWindow(toolWindow: ToolWindow) {
    init(toolWindow)
    val changesViewContentManager = ChangesViewContentManager.getInstance(project)
    ChangesViewExtensionsManager(project, changesViewContentManager, project)
    changesViewContentManager.setContentManager(toolWindow.contentManager)
  }

  /**
   * This should be done in ChangesViewToolWindowFactory.init but there's no project
   */
  private fun init(toolWindow: ToolWindow) {
    updateAvailability(toolWindow)
    project.messageBus.connect().subscribe(ProjectLevelVcsManager.VCS_CONFIGURATION_CHANGED,
                                           VcsListener { runInEdt { updateAvailability(toolWindow) } })
  }

  override fun shouldBeAvailable(): Boolean {
    return ProjectLevelVcsManager.getInstance(project).hasAnyMappings()
  }

  private fun updateAvailability(toolWindow: ToolWindow) {
    val available = shouldBeAvailable()
    if (available != toolWindow.isAvailable) {
      toolWindow.isShowStripeButton = true
      toolWindow.setAvailable(available, null)
    }
  }
}