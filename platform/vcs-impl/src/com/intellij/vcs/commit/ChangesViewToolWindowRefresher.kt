// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.commit

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ChangeListManagerRefreshHelper
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager.Companion.LOCAL_CHANGES
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ex.ToolWindowManagerListener

private class ChangesViewToolWindowRefresher(private val project: Project) : ToolWindowManagerListener {
  override fun toolWindowShown(toolWindow: ToolWindow) {
    if (ChangesViewContentManager.getToolWindowIdFor(project, LOCAL_CHANGES) == toolWindow.id) {
      ChangeListManagerRefreshHelper.requestRefresh(project)
    }
  }
}