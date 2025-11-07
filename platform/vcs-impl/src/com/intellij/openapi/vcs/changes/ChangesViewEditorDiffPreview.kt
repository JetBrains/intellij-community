// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes

import com.intellij.diff.chains.DiffRequestProducer
import com.intellij.diff.impl.DiffEditorViewer
import com.intellij.diff.tools.combined.CombinedDiffComponentProcessor
import com.intellij.openapi.ListSelection
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.VcsApplicationSettings
import com.intellij.openapi.vcs.VcsBundle.message
import com.intellij.openapi.vcs.VcsConfiguration
import com.intellij.openapi.vcs.changes.actions.diff.ShowDiffFromLocalChangesActionProvider
import com.intellij.openapi.vcs.changes.actions.diff.WrapperCombinedBlockProducer
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager.Companion.LOCAL_CHANGES
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager.Companion.isToolWindowTabVertical
import com.intellij.openapi.vcs.changes.ui.CommitToolWindowUtil
import com.intellij.vcs.changes.viewModel.ChangesViewProxy
import org.jetbrains.annotations.CalledInAny
import javax.swing.JComponent

internal class ChangesViewEditorDiffPreview(
  val changesView: ChangesViewProxy,
  targetComponent: JComponent,
) : EditorTabDiffPreview(changesView.project) {
  init {
    PreviewOnNextDiffAction().registerCustomShortcutSet(targetComponent, this)
  }

  fun handleSingleClick() {
    if (!isOpenPreviewWithSingleClick()) return

    val opened = openPreview(false)
    if (!opened) closePreview()
  }

  override fun hasContent(): Boolean = ChangesViewDiffPreviewHandler.hasContent(changesView.getTree())

  override fun createViewer(): DiffEditorViewer = changesView.createDiffPreviewProcessor(true)

  override fun collectDiffProducers(selectedOnly: Boolean): ListSelection<out DiffRequestProducer> =
    ChangesViewDiffPreviewHandler.collectDiffProducers(changesView.getTree(), selectedOnly)

  override fun handleEscapeKey() {
    closePreview()
    returnFocusToComponent()
  }

  fun isPreviewOnDoubleClickOrEnter(): Boolean =
    if (ChangesViewContentManager.isCommitToolWindowShown(project))
      VcsApplicationSettings.getInstance().SHOW_EDITOR_PREVIEW_ON_DOUBLE_CLICK
    else
      VcsApplicationSettings.getInstance().SHOW_DIFF_ON_DOUBLE_CLICK

  private fun isOpenPreviewWithSingleClick(): Boolean =
    !isSplitterPreviewPresent() &&
    Registry.get("show.diff.preview.as.editor.tab.with.single.click").asBoolean() &&
    !changesView.isModelUpdateInProgress() &&
    VcsConfiguration.getInstance(project).LOCAL_CHANGES_DETAILS_PREVIEW_SHOWN


  private fun returnFocusToComponent() {
    ChangesViewContentManager.getToolWindowFor(project, ChangesViewContentManager.LOCAL_CHANGES)?.activate(null)
  }

  override fun openPreview(requestFocus: Boolean): Boolean =
    CommitToolWindowUtil.openDiff(ChangesViewContentManager.LOCAL_CHANGES, this, requestFocus)

  override fun updateDiffAction(event: AnActionEvent) {
    ShowDiffFromLocalChangesActionProvider.updateAvailability(event)
  }

  @CalledInAny
  override fun getEditorTabName(processor: DiffEditorViewer?): String? {
    val wrapper = when (processor) {
      is ChangeViewDiffRequestProcessor -> processor.currentChange
      is CombinedDiffComponentProcessor -> (processor.currentBlock as? WrapperCombinedBlockProducer)?.wrapper
      else -> null
    }
    return if (wrapper != null) message("commit.editor.diff.preview.title", wrapper.presentableName)
    else message("commit.editor.diff.preview.empty.title")
  }

  private fun isSplitterPreviewPresent() =
    ChangesViewContentManager.shouldHaveSplitterDiffPreview(project, isToolWindowTabVertical(project, LOCAL_CHANGES))

  private inner class PreviewOnNextDiffAction : DumbAwareAction() {
    init {
      copyShortcutFrom(ActionManager.getInstance().getAction(IdeActions.ACTION_NEXT_DIFF))
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
      e.presentation.isEnabledAndVisible = true
    }

    override fun actionPerformed(e: AnActionEvent) {
      openPreview(true)
    }
  }
}
