// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight

import com.intellij.codeInsight.daemon.HighlightingPassesCache
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener

internal class ChangesToolwindowPassCache : ProjectActivity {
  override suspend fun execute(project: Project) {
    if (!Registry.`is`("highlighting.passes.cache")) return

    project.messageBus.connect().subscribe(ToolWindowManagerListener.TOPIC, object : ToolWindowManagerListener {
      override fun stateChanged(toolWindowManager: ToolWindowManager, toolWindow: ToolWindow,
                                changeType: ToolWindowManagerListener.ToolWindowManagerEventType) {
        if (changeType == ToolWindowManagerListener.ToolWindowManagerEventType.ActivateToolWindow && ToolWindowId.COMMIT == toolWindow.id) {
          val changedFiles = ChangeListManager.getInstance(project).defaultChangeList.changes.mapNotNull { it.virtualFile }

          HighlightingPassesCache.getInstance(project).schedule(changedFiles)
        }
      }
    })
  }
}
