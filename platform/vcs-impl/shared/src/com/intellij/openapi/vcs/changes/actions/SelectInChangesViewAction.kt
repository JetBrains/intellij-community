// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.vcs.changes.ui.ChangesListView
import com.intellij.openapi.vcs.changes.ui.ChangesTree
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.platform.vcs.VcsUtil
import com.intellij.util.ui.tree.TreeUtil

internal class SelectInChangesViewAction : DumbAwareAction(), ActionRemoteBehaviorSpecification.FrontendOtherwiseBackend {
  override fun actionPerformed(event: AnActionEvent) {
    val view = event.getData(ChangesListView.DATA_KEY) ?: return
    val file = event.getData(PlatformDataKeys.LAST_ACTIVE_FILE_EDITOR)?.file ?: return
    view.selectFile(file)
    IdeFocusManager.getInstance(event.project).requestFocus(view, false)
  }

  override fun update(event: AnActionEvent) {
    val view = event.getData(ChangesListView.DATA_KEY)
    if (view == null || TreeUtil.hasManyNodes(view, ChangesTree.EXPAND_NODES_THRESHOLD)) {
      event.presentation.isEnabledAndVisible = false
      return
    }
    event.presentation.isVisible = true

    val file = event.getData(PlatformDataKeys.LAST_ACTIVE_FILE_EDITOR)?.file
    event.presentation.isEnabled = file != null && view.containsFile(VcsUtil.getFilePath(file))
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
}