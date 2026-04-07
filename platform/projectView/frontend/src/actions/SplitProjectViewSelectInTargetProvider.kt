// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.frontend.actions

import com.intellij.ide.SelectInTarget
import com.intellij.ide.impl.ProjectViewSelectInTargetProvider
import com.intellij.ide.projectView.impl.isProjectViewSplit
import com.intellij.openapi.project.Project
import com.intellij.platform.projectView.frontend.window.ProjectViewToolWindowServiceImpl

internal class SplitProjectViewSelectInTargetProvider : ProjectViewSelectInTargetProvider {
  override fun getSelectInTargets(project: Project): Collection<SelectInTarget> {
    if (!isProjectViewSplit()) return emptyList()
    return buildList {
      val toolWindowService = ProjectViewToolWindowServiceImpl.getInstance(project)
      val currentPaneId = toolWindowService.currentPaneId
      val panes = toolWindowService.panes.values.toList()
      panes.filter { it.id == currentPaneId }.forEach { pane ->
        addAll(pane.selectInTargets.sortedBy { it.weight })
      }
      panes.filter { it.id != currentPaneId }.forEach { pane ->
        addAll(pane.selectInTargets.sortedBy { it.weight })
      }
    }
  }
}
