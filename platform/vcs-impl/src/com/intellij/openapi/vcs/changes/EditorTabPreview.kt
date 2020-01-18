// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes

import com.intellij.diff.impl.DiffRequestProcessor
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.CommonShortcuts.ESCAPE
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.actions.diff.lst.LocalChangeListDiffTool.ALLOW_EXCLUDE_FROM_COMMIT
import com.intellij.openapi.vcs.changes.ui.ChangesTree
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update
import javax.swing.JComponent

abstract class EditorTabPreview(private val diffProcessor: DiffRequestProcessor) : ChangesViewPreview {
  private val project get() = diffProcessor.project!!
  private val previewFile = PreviewDiffVirtualFile(EditorTabDiffPreviewProvider(diffProcessor) { getCurrentName() })
  private val updatePreviewQueue =
    MergingUpdateQueue("updatePreviewQueue", 100, true, MergingUpdateQueue.ANY_COMPONENT, project, null, true).apply {
      setRestartTimerOnAdd(true)
    }

  var escapeHandler: Runnable? = null

  fun installOn(tree: ChangesTree) =
    //do not open file aggressively on start up, do it later
    DumbService.getInstance(project).smartInvokeLater {
      tree.addSelectionListener {
        updatePreviewQueue.queue(Update.create(this) {
          if (skipPreviewUpdate()) return@create
          setPreviewVisible(true)
        })
      }
    }

  fun installNextDiffActionOn(component: JComponent) {
    DumbAwareAction.create { openPreview(true) }.apply {
      copyShortcutFrom(ActionManager.getInstance().getAction(IdeActions.ACTION_NEXT_DIFF))
      registerCustomShortcutSet(component, null)
    }
  }

  protected abstract fun getCurrentName(): String?

  protected abstract fun hasContent(): Boolean

  protected open fun skipPreviewUpdate(): Boolean = ToolWindowManager.getInstance(project).isEditorComponentActive

  override fun updatePreview(fromModelRefresh: Boolean) {
    (diffProcessor as? DiffPreviewUpdateProcessor)?.refresh(false)
    if (!hasContent()) closePreview()
  }

  override fun setPreviewVisible(isPreviewVisible: Boolean) {
    updatePreview(false)
    if (isPreviewVisible) openPreview(false) else closePreview()
  }

  override fun setAllowExcludeFromCommit(value: Boolean) {
    diffProcessor.putContextUserData(ALLOW_EXCLUDE_FROM_COMMIT, value)
    diffProcessor.updateRequest(true)
  }

  fun closePreview() = FileEditorManager.getInstance(project).closeFile(previewFile)

  fun openPreview(focusEditor: Boolean) {
    if (!hasContent()) return

    openPreview(project, previewFile, focusEditor, escapeHandler)
  }

  companion object {
    fun openPreview(project: Project, file: PreviewDiffVirtualFile, focusEditor: Boolean, escapeHandler: Runnable? = null) {
      val wasAlreadyOpen = FileEditorManager.getInstance(project).isFileOpen(file)
      val editor = FileEditorManager.getInstance(project).openFile(file, focusEditor, true).singleOrNull() ?: return

      if (wasAlreadyOpen) return
      escapeHandler?.let { r -> DumbAwareAction.create { r.run() }.registerCustomShortcutSet(ESCAPE, editor.component, editor) }
    }
  }
}

private class EditorTabDiffPreviewProvider(
  private val diffProcessor: DiffRequestProcessor,
  private val tabNameProvider: () -> String?
) : DiffPreviewProvider {

  override fun createDiffRequestProcessor(): DiffRequestProcessor = diffProcessor

  override fun getOwner(): Any = this

  override fun getEditorTabName(): String = tabNameProvider().orEmpty()
}