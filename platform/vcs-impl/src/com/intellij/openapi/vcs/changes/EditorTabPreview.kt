// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes

import com.intellij.diff.impl.DiffRequestProcessor
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonShortcuts
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.changes.actions.diff.lst.LocalChangeListDiffTool.ALLOW_EXCLUDE_FROM_COMMIT
import com.intellij.openapi.vcs.changes.ui.ChangesTree
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update
import javax.swing.JComponent

abstract class EditorTabPreview(
  private val diffProcessor: DiffRequestProcessor,
  contentPanel: JComponent,
  changesTree: ChangesTree
) : ChangesViewPreview {

  private val project get() = diffProcessor.project!!
  private val previewFile = PreviewDiffVirtualFile(EditorTabDiffPreviewProvider(diffProcessor) { getCurrentName() })
  private val updatePreviewQueue =
    MergingUpdateQueue("updatePreviewQueue", 100, true, MergingUpdateQueue.ANY_COMPONENT, project, null, true).apply {
      setRestartTimerOnAdd(true)
    }

  init {
    //do not open file aggressively on start up, do it later
    DumbService.getInstance(project).smartInvokeLater {
      if (project.isDisposed) return@smartInvokeLater

      changesTree.addSelectionListener {
        updatePreviewQueue.queue(Update.create(this) {
          if (skipPreviewUpdate()) return@create
          setPreviewVisible(true)
        })
      }
    }
    object : DumbAwareAction() {
      init {
        copyShortcutFrom(ActionManager.getInstance().getAction(IdeActions.ACTION_NEXT_DIFF))
      }

      override fun actionPerformed(e: AnActionEvent) {
        openPreview(true)
      }
    }.registerCustomShortcutSet(contentPanel, null)
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

  fun openPreview(focus: Boolean) {
    if (hasContent()) {
      val wasOpen = FileEditorManager.getInstance(project).isFileOpen(previewFile)
      val fileEditors = FileEditorManager.getInstance(project).openFile(previewFile, focus, true)
      if (!wasOpen) {
        val action = object : DumbAwareAction() {
          init {
            shortcutSet = CommonShortcuts.ESCAPE
          }

          override fun actionPerformed(e: AnActionEvent) {
            ToolWindowManager.getInstance(project).getToolWindow("Commit")!!.activate {}
          }
        }
        action.registerCustomShortcutSet(fileEditors[0].component, null)
        Disposer.register(fileEditors[0], Disposable { action.unregisterCustomShortcutSet(fileEditors[0].component) })
      }
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