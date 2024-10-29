// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes

import com.intellij.diff.tools.util.DiffDataKeys
import com.intellij.idea.ActionsBundle.message
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.AnActionExtensionProvider

internal class ShowEditorDiffPreviewActionProvider : AnActionExtensionProvider {
  override fun isActive(e: AnActionEvent): Boolean {
    val project = e.project

    return project != null &&
           getDiffPreview(e) != null
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun update(e: AnActionEvent) {
    val diffPreview = getDiffPreview(e)!!
    e.presentation.description += " " + message("action.Diff.ShowDiffPreview.description")
    diffPreview.updateDiffAction(e)
  }

  override fun actionPerformed(e: AnActionEvent) {
    val diffPreview = getDiffPreview(e)!!
    diffPreview.performDiffAction()
  }

  private fun getDiffPreview(e: AnActionEvent): DiffPreview? = e.getData(DiffDataKeys.EDITOR_TAB_DIFF_PREVIEW)
}
