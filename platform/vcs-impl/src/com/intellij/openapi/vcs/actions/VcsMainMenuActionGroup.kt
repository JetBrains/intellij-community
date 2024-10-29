// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.vcs.ProjectLevelVcsManager

internal class VcsMainMenuActionGroup : DefaultActionGroup(), DumbAware {
  override fun update(e: AnActionEvent) {
    val project = e.project ?: return
    val vcs = ProjectLevelVcsManager.getInstance(project).singleVCS ?: return
    e.presentation.isEnabledAndVisible = !vcs.isWithCustomMenu
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }
}
