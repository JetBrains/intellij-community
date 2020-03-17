// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.ProjectLevelVcsManager.VCS_CONFIGURATION_CHANGED
import com.intellij.openapi.vcs.VcsListener
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.impl.ToolWindowImpl

private val ToolWindow.project: Project? get() = (this as? ToolWindowImpl)?.toolWindowManager?.project

abstract class VcsToolWindowFactory : ToolWindowFactory, DumbAware {
  override fun init(window: ToolWindow) {
    val project = window.project ?: return

    updateState(project, window)
    project.messageBus.connect().subscribe(VCS_CONFIGURATION_CHANGED, VcsListener {
      runInEdt {
        if (project.isDisposed) return@runInEdt

        updateState(project, window)
      }
    })
  }

  override fun shouldBeAvailable(project: Project): Boolean = project.vcsManager.hasAnyMappings()

  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) =
    with(ChangesViewContentManager.getInstance(project)) {
      ChangesViewExtensionsManager(project, this, project)
      setContentManager(toolWindow.contentManager)
    }

  protected open fun updateState(project: Project, toolWindow: ToolWindow) {
    val available = shouldBeAvailable(project)

    // force showing stripe button on adding initial mapping even if stripe button was manually removed by the user
    if (available && !toolWindow.isAvailable) {
      toolWindow.isShowStripeButton = true
    }

    toolWindow.setAvailable(available, null)
  }

  companion object {
    internal val Project.vcsManager: ProjectLevelVcsManager get() = ProjectLevelVcsManager.getInstance(this)
  }
}