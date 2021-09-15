// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.impl

import com.intellij.ide.impl.ContentManagerWatcher
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.wm.RegisterToolWindowTask
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.impl.content.ToolWindowContentUi
import com.intellij.ui.content.Content

object ChangesBrowserToolWindow {
  const val TOOLWINDOW_ID: String = "VcsChanges" // NON-NLS

  @JvmStatic
  fun showTab(project: Project, content: Content) {
    val toolWindowManager = ToolWindowManager.getInstance(project)
    val repoToolWindow = toolWindowManager.getToolWindow(TOOLWINDOW_ID) ?: registerRepositoriesToolWindow(toolWindowManager)

    repoToolWindow.contentManager.removeAllContents(true)
    repoToolWindow.contentManager.addContent(content)
    repoToolWindow.activate(null)
  }

  private fun registerRepositoriesToolWindow(toolWindowManager: ToolWindowManager): ToolWindow {
    val toolWindow = toolWindowManager.registerToolWindow(RegisterToolWindowTask(
      id = TOOLWINDOW_ID,
      anchor = ToolWindowAnchor.LEFT,
      canCloseContent = true,
      canWorkInDumbMode = true,
      stripeTitle = { VcsBundle.message("ChangesBrowserToolWindow.toolwindow.name") }
    ))
    toolWindow.component.putClientProperty(ToolWindowContentUi.HIDE_ID_LABEL, "true")
    ContentManagerWatcher.watchContentManager(toolWindow, toolWindow.contentManager)
    return toolWindow
  }
}
