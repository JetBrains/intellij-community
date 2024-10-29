// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.actions

import com.intellij.ide.impl.isTrusted
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.vcs.ProjectLevelVcsManager

internal class VcsGroupsActionGroup : DefaultActionGroup(), DumbAware {

  override fun update(e: AnActionEvent) {
    val presentation = e.presentation
    val project = e.project
    if (project != null) {
      presentation.text = ProjectLevelVcsManager.getInstance(project).consolidatedVcsName
    }
    presentation.isEnabledAndVisible = project != null && project.isTrusted()
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }
}
