// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.action

import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.codereview.changes.CodeReviewChangeListComponentFactory.SELECTED_CHANGES
import com.intellij.collaboration.ui.codereview.details.model.isViewedStateForAllChanges
import com.intellij.collaboration.util.getOrNull
import com.intellij.diff.tools.util.DiffDataKeys
import com.intellij.diff.tools.util.base.DiffViewerBase
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.DumbAwareAction
import org.jetbrains.plugins.github.pullrequest.ui.details.model.GHPRChangeListViewModel
import org.jetbrains.plugins.github.pullrequest.ui.diff.GHPRDiffReviewViewModel
import org.jetbrains.plugins.github.pullrequest.ui.editor.GHPRReviewFileEditorViewModel

internal class GHPRViewedStateToggleAction : DumbAwareAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = false
    e.presentation.description = CollaborationToolsBundle.message("action.CodeReview.ToggleChangesViewed.description")

    val changes = e.getData(SELECTED_CHANGES)
    val changesVm = e.getData(GHPRChangeListViewModel.DATA_KEY)
    if (changes != null && changesVm != null) {
      if (!changesVm.isOnLatest) return

      val isAllViewed = changesVm.isViewedStateForAllChanges(changes, viewed = true)
      e.presentation.text =
        if (changes.size == 1) {
          if (isAllViewed) CollaborationToolsBundle.message("action.CodeReview.ToggleChangesViewed.markNotViewed")
          else CollaborationToolsBundle.message("action.CodeReview.ToggleChangesViewed.markViewed")
        }
        else {
          if (isAllViewed) CollaborationToolsBundle.message("action.CodeReview.ToggleChangesViewed.markAllNotViewed")
          else CollaborationToolsBundle.message("action.CodeReview.ToggleChangesViewed.markAllViewed")
        }
      e.presentation.isEnabledAndVisible = true
      return
    }

    val fileEditor = e.getData(CommonDataKeys.EDITOR)
    val fileVm = fileEditor?.getUserData(GHPRReviewFileEditorViewModel.KEY)
    if (fileVm != null) {
      if (fileVm.isUpdateRequired.value) return
      val isViewed = fileVm.isViewedState.value.getOrNull() ?: return

      e.presentation.text = if (isViewed) CollaborationToolsBundle.message("action.CodeReview.ToggleChangesViewed.markFileNotViewed") else CollaborationToolsBundle.message("action.CodeReview.ToggleChangesViewed.markFileViewed")
      e.presentation.isEnabledAndVisible = true
      e.presentation.icon = AllIcons.Vcs.Vendors.Github
      return
    }

    val diffViewer = e.getData(DiffDataKeys.DIFF_VIEWER) as? DiffViewerBase
    val diffVm = diffViewer?.context?.getUserData(GHPRDiffReviewViewModel.KEY)
    if (diffVm != null) {
      val isViewed = diffVm.isViewedState.value.getOrNull() ?: return

      e.presentation.text = if (isViewed) CollaborationToolsBundle.message("action.CodeReview.ToggleChangesViewed.markFileNotViewed") else CollaborationToolsBundle.message("action.CodeReview.ToggleChangesViewed.markFileViewed")
      e.presentation.isEnabledAndVisible = true
      return
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    val changes = e.getData(SELECTED_CHANGES)
    val changesVm = e.getData(GHPRChangeListViewModel.DATA_KEY)
    if (changes != null && changesVm != null) {
      if (!changesVm.isOnLatest) return

      val isAllViewed = changesVm.isViewedStateForAllChanges(changes, viewed = true)
      changesVm.setViewedState(changes, !isAllViewed)
      return
    }

    val fileEditor = e.getData(CommonDataKeys.EDITOR)
    val fileVm = fileEditor?.getUserData(GHPRReviewFileEditorViewModel.KEY)
    if (fileVm != null) {
      if (fileVm.isUpdateRequired.value) return
      val isViewed = fileVm.isViewedState.value.getOrNull() ?: return
      fileVm.setViewedState(!isViewed)
      return
    }

    val diffViewer = e.getData(DiffDataKeys.DIFF_VIEWER) as? DiffViewerBase
    val diffVm = diffViewer?.context?.getUserData(GHPRDiffReviewViewModel.KEY)
    if (diffVm != null) {
      val isViewed = diffVm.isViewedState.value.getOrNull() ?: return
      diffVm.setViewedState(!isViewed)
      return
    }
  }
}