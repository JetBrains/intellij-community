// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.index.ui

import com.intellij.diff.impl.DiffEditorViewer
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.vcs.VcsApplicationSettings
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.changes.ChangeViewDiffRequestProcessor
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager.Companion.isCommitToolWindowShown
import com.intellij.openapi.vcs.changes.ui.TreeHandlerEditorDiffPreview
import git4idea.index.GitStageTracker
import git4idea.index.actions.updateStageDiffAvailability
import javax.swing.JComponent

class GitStageEditorDiffPreview(
  val stageTree: GitStageTree,
  val tracker: GitStageTracker,
  val toolbarSizeReferent: JComponent,
  val activate: () -> Unit
) : TreeHandlerEditorDiffPreview(stageTree, GitStageDiffPreviewHandler) {

  override fun createViewer(): DiffEditorViewer {
    val processor = GitStageDiffRequestProcessor(stageTree, tracker, true)
    processor.setToolbarVerticalSizeReferent(toolbarSizeReferent)
    return processor
  }

  override fun updateDiffAction(event: AnActionEvent) {
    updateStageDiffAvailability(event)
  }

  override fun getEditorTabName(wrapper: ChangeViewDiffRequestProcessor.Wrapper?): String {
    return wrapper?.presentableName?.let { changeName ->
      VcsBundle.message("commit.editor.diff.preview.title", changeName)
    } ?: VcsBundle.message("commit.editor.diff.preview.empty.title")
  }

  override fun returnFocusToTree() {
    activate()
  }

  override fun isPreviewOnDoubleClick(): Boolean {
    if (isCommitToolWindowShown(project)) {
      return VcsApplicationSettings.getInstance().SHOW_EDITOR_PREVIEW_ON_DOUBLE_CLICK
    }
    else {
      return VcsApplicationSettings.getInstance().SHOW_DIFF_ON_DOUBLE_CLICK
    }
  }

  override fun isPreviewOnEnter(): Boolean {
    if (isCommitToolWindowShown(project)) {
      return VcsApplicationSettings.getInstance().SHOW_EDITOR_PREVIEW_ON_DOUBLE_CLICK
    }
    else {
      return VcsApplicationSettings.getInstance().SHOW_DIFF_ON_DOUBLE_CLICK
    }
  }
}
