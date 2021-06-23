// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareToggleAction
import com.intellij.openapi.vcs.VcsApplicationSettings
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.changes.ChangesViewManager.isEditorPreview

private abstract class ShowOnDoubleClickToggleAction(private val isEditorPreview: Boolean) : DumbAwareToggleAction() {
  override fun update(e: AnActionEvent) {
    super.update(e)

    val changeListManager = e.project?.let { ChangeListManager.getInstance(it) }
    val isEditorPreview = e.project?.let { isEditorPreview(it) } == true

    e.presentation.isEnabledAndVisible =
      isEditorPreview || changeListManager?.areChangeListsEnabled() == false
  }

  override fun isSelected(e: AnActionEvent): Boolean =
    VcsApplicationSettings.getInstance().SHOW_EDITOR_PREVIEW_ON_DOUBLE_CLICK == isEditorPreview

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    VcsApplicationSettings.getInstance().SHOW_EDITOR_PREVIEW_ON_DOUBLE_CLICK = isEditorPreview
  }

  class EditorPreview : ShowOnDoubleClickToggleAction(true)

  class Source : ShowOnDoubleClickToggleAction(false)
}
