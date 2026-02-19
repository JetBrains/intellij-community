// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.changes.ChangeListManagerRefreshHelper
import com.intellij.util.concurrency.annotations.RequiresEdt

class RefreshAction : AnAction(), DumbAware {
  override fun update(e: AnActionEvent) {
    val project = e.project
    e.presentation.isEnabledAndVisible = project != null && ProjectLevelVcsManager.getInstance(project).hasActiveVcss()
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    ChangeListManagerRefreshHelper.launchRefreshOrNotifyFrozen(project)
  }

  @Suppress("CompanionObjectInExtension")
  companion object {
    @Deprecated("Use ChangeListManagerRefreshHelper instead",
                replaceWith = ReplaceWith("ChangeListManagerRefreshHelper.launchRefreshOrNotifyFrozen(project)",
                                          "com.intellij.openapi.vcs.changes.ChangeListManagerRefreshHelper"))
    @JvmStatic
    @RequiresEdt
    fun doRefresh(project: Project) {
      ChangeListManagerRefreshHelper.launchRefreshOrNotifyFrozen(project)
    }
  }
}
