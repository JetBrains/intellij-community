// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.index.ui

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.vcs.VcsApplicationSettings
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.changes.ChangeViewDiffRequestProcessor
import com.intellij.openapi.vcs.changes.EditorTabPreview
import com.intellij.openapi.vcs.changes.ui.ChangesTree
import com.intellij.openapi.wm.IdeFocusManager
import git4idea.index.actions.GitStageDiffAction

class GitStageEditorDiffPreview(diffProcessor: GitStageDiffPreview, private val tree: ChangesTree) : EditorTabPreview(diffProcessor) {
  private val changeViewProcessor: ChangeViewDiffRequestProcessor get() = diffProcessor as ChangeViewDiffRequestProcessor

  override fun hasContent(): Boolean {
    return changeViewProcessor.currentChange != null
  }

  override fun updateAvailability(event: AnActionEvent) {
    GitStageDiffAction.updateAvailability(event)
  }

  override fun getCurrentName(): String {
    return changeViewProcessor.currentChangeName?.let { changeName -> VcsBundle.message("commit.editor.diff.preview.title", changeName) }
           ?: VcsBundle.message("commit.editor.diff.preview.empty.title")
  }

  override fun skipPreviewUpdate(): Boolean {
    return super.skipPreviewUpdate() || tree != IdeFocusManager.getInstance(project).focusOwner
  }

  internal fun processDoubleClickOrEnter(isDoubleClick: Boolean): Boolean {
    val isPreviewAllowed = if (isDoubleClick) isPreviewOnDoubleClickAllowed() else isPreviewOnEnterAllowed()
    return isPreviewAllowed && openPreview(isDoubleClick)
  }

  override fun isPreviewOnDoubleClickAllowed(): Boolean = VcsApplicationSettings.getInstance().SHOW_EDITOR_PREVIEW_ON_DOUBLE_CLICK
  override fun isPreviewOnEnterAllowed(): Boolean = VcsApplicationSettings.getInstance().SHOW_EDITOR_PREVIEW_ON_DOUBLE_CLICK
}
