// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareToggleAction
import com.intellij.openapi.vcs.VcsApplicationSettings
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager.Companion.isCommitToolWindowShown

internal sealed class ShowOnDoubleClickToggleAction(private val isEditorPreview: Boolean) : DumbAwareToggleAction() {
  override fun isSelected(e: AnActionEvent): Boolean {
    val isCommitToolwindowShown = e.project?.let(::isCommitToolWindowShown) == true
    if (isCommitToolwindowShown) {
      return VcsApplicationSettings.getInstance().SHOW_EDITOR_PREVIEW_ON_DOUBLE_CLICK == isEditorPreview
    }
    else {
      return VcsApplicationSettings.getInstance().SHOW_DIFF_ON_DOUBLE_CLICK == isEditorPreview
    }
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    // Make the action behave as "Option" in "ShowOnDoubleClick" group.
    val newState = if (e.isFromContextMenu || state) isEditorPreview else !isEditorPreview

    val isCommitToolwindowShown = e.project?.let(::isCommitToolWindowShown) == true
    if (isCommitToolwindowShown) {
      VcsApplicationSettings.getInstance().SHOW_EDITOR_PREVIEW_ON_DOUBLE_CLICK = newState
    }
    else {
      VcsApplicationSettings.getInstance().SHOW_DIFF_ON_DOUBLE_CLICK = newState
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.EDT
  }

  class EditorPreview : ShowOnDoubleClickToggleAction(true)
  class Source : ShowOnDoubleClickToggleAction(false)
}

