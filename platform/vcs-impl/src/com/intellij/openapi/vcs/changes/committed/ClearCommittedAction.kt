// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.committed

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager

class ClearCommittedAction : AnAction("Clear", "Clears cached revisions", AllIcons.Vcs.Remove), DumbAware {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.getData(CommonDataKeys.PROJECT)!!
    val panel = ChangesViewContentManager.getInstance(project).getActiveComponent(CommittedChangesPanel::class.java)!!

    panel.clearCaches()
  }

  override fun update(e: AnActionEvent) {
    val project = e.getData(CommonDataKeys.PROJECT)
    val panel = project?.let { ChangesViewContentManager.getInstance(it).getActiveComponent(CommittedChangesPanel::class.java) }

    e.presentation.isEnabledAndVisible = panel != null && !panel.isInLoad && panel.repositoryLocation == null
  }
}
