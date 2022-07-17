// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareToggleAction
import com.intellij.openapi.vcs.VcsApplicationSettings
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager.Companion.isCommitToolWindowShown

private object ShowOnDoubleClickToggleAction {

  sealed class CommitView(private val isEditorPreview: Boolean) : DumbAwareToggleAction() {

    override fun isSelected(e: AnActionEvent): Boolean =
      VcsApplicationSettings.getInstance().SHOW_EDITOR_PREVIEW_ON_DOUBLE_CLICK == isEditorPreview

    override fun setSelected(e: AnActionEvent, state: Boolean) {
      VcsApplicationSettings.getInstance().SHOW_EDITOR_PREVIEW_ON_DOUBLE_CLICK = isEditorPreview
    }

    override fun update(e: AnActionEvent) {
      super.update(e)

      val isCommitToolwindowShown = e.project?.let(::isCommitToolWindowShown) == true
      e.presentation.isEnabledAndVisible = isCommitToolwindowShown
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
      return ActionUpdateThread.EDT
    }

    class EditorPreview : CommitView(true)
    class Source : CommitView(false)
  }

  sealed class LocalChangesView(private val isEditorPreview: Boolean) : DumbAwareToggleAction() {

    override fun isSelected(e: AnActionEvent): Boolean =
      VcsApplicationSettings.getInstance().SHOW_DIFF_ON_DOUBLE_CLICK == isEditorPreview

    override fun setSelected(e: AnActionEvent, state: Boolean) {
      VcsApplicationSettings.getInstance().SHOW_DIFF_ON_DOUBLE_CLICK = isEditorPreview
    }

    override fun update(e: AnActionEvent) {
      super.update(e)

      val isCommitToolwindowShown = e.project?.let(::isCommitToolWindowShown) == true
      e.presentation.isEnabledAndVisible = !isCommitToolwindowShown
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
      return ActionUpdateThread.EDT
    }

    class EditorPreview : LocalChangesView(true)
    class Source : LocalChangesView(false)
  }
}

