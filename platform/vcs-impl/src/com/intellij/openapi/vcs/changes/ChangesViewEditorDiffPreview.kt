// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes

import com.intellij.diff.impl.DiffEditorViewer
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.VcsApplicationSettings
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.VcsConfiguration
import com.intellij.openapi.vcs.changes.ChangeViewDiffRequestProcessor.Wrapper
import com.intellij.openapi.vcs.changes.actions.diff.ShowDiffFromLocalChangesActionProvider
import com.intellij.openapi.vcs.changes.ui.ChangesTree
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager
import com.intellij.openapi.vcs.changes.ui.CommitToolWindowUtil
import com.intellij.openapi.vcs.changes.ui.TreeHandlerEditorDiffPreview
import javax.swing.JComponent

internal class ChangesViewEditorDiffPreview(
  tree: ChangesTree,
  targetComponent: JComponent,
  private val diffViewerFactory: () -> DiffEditorViewer,
  private val isSplitterPreviewPresent: () -> Boolean,
) : TreeHandlerEditorDiffPreview(tree, targetComponent, ChangesViewDiffPreviewHandler) {

  override fun createViewer(): DiffEditorViewer = diffViewerFactory()

  override fun returnFocusToComponent() {
    ChangesViewContentManager.getToolWindowFor(project, ChangesViewContentManager.LOCAL_CHANGES)?.activate(null)
  }

  override fun openPreview(requestFocus: Boolean): Boolean =
    CommitToolWindowUtil.openDiff(ChangesViewContentManager.LOCAL_CHANGES, this, requestFocus)

  override fun updateDiffAction(event: AnActionEvent) {
    ShowDiffFromLocalChangesActionProvider.updateAvailability(event)
  }

  override fun getEditorTabName(wrapper: Wrapper?): String =
    if (wrapper != null) VcsBundle.message("commit.editor.diff.preview.title", wrapper.presentableName)
    else VcsBundle.message("commit.editor.diff.preview.empty.title")

  override fun isOpenPreviewWithSingleClick(): Boolean =
    !isSplitterPreviewPresent() &&
    Registry.get("show.diff.preview.as.editor.tab.with.single.click").asBoolean() &&
    !tree.isModelUpdateInProgress &&
    VcsConfiguration.getInstance(project).LOCAL_CHANGES_DETAILS_PREVIEW_SHOWN

  override fun isPreviewOnDoubleClickOrEnter(): Boolean =
    if (ChangesViewContentManager.isCommitToolWindowShown(project)) VcsApplicationSettings.getInstance().SHOW_EDITOR_PREVIEW_ON_DOUBLE_CLICK
    else VcsApplicationSettings.getInstance().SHOW_DIFF_ON_DOUBLE_CLICK
}
