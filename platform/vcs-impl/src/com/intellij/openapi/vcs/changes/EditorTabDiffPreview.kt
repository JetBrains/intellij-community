// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes

import com.intellij.diff.chains.DiffRequestProducer
import com.intellij.diff.editor.DiffEditorTabFilesManager
import com.intellij.diff.editor.DiffEditorViewerFileEditor
import com.intellij.diff.editor.DiffViewerVirtualFile
import com.intellij.diff.editor.DiffVirtualFileWithTabName
import com.intellij.diff.impl.DiffEditorViewer
import com.intellij.diff.tools.external.ExternalDiffTool
import com.intellij.openapi.ListSelection
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diff.DiffBundle
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.CheckedDisposable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.changes.EditorTabPreviewBase.Companion.showExternalToolIfNeeded
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.Nls

abstract class EditorTabDiffPreview(val project: Project) : CheckedDisposable, DiffPreview {
  abstract fun hasContent(): Boolean
  protected abstract fun createViewer(): DiffEditorViewer
  protected abstract fun collectDiffProducers(selectedOnly: Boolean): ListSelection<out DiffRequestProducer>?
  protected abstract fun getEditorTabName(processor: DiffEditorViewer?): @Nls String?


  private var _isDisposed = false
  override fun isDisposed(): Boolean = _isDisposed
  override fun dispose() {
    _isDisposed = true
  }


  val previewFile: VirtualFile = TabPreviewDiffVirtualFile(this)

  fun isPreviewOpen(): Boolean {
    return FileEditorManager.getInstance(project).isFileOpenWithRemotes(previewFile)
  }

  override fun closePreview() {
    DiffPreview.closePreviewFile(project, previewFile)
  }

  override fun openPreview(requestFocus: Boolean): Boolean {
    if (!hasContent()) return false
    DiffEditorTabFilesManager.getInstance(project).showDiffFile(previewFile, requestFocus)
    return true
  }


  override fun updateDiffAction(event: AnActionEvent) {
    event.presentation.isVisible = event.isFromActionToolbar || event.presentation.isEnabled
  }

  override fun performDiffAction(): Boolean {
    if (!hasContent()) return false

    if (ExternalDiffTool.isEnabled()) {
      var diffProducers = collectDiffProducers(true)
      if (diffProducers != null && diffProducers.isEmpty) {
        diffProducers = collectDiffProducers(false)
      }
      if (showExternalToolIfNeeded(project, diffProducers)) {
        return true
      }
    }

    return openPreview(true)
  }


  private class TabPreviewDiffVirtualFile(preview: EditorTabDiffPreview)
    : DiffViewerVirtualFile("TabPreviewDiffVirtualFile"), DiffVirtualFileWithTabName {
    private var _preview: EditorTabDiffPreview? = preview

    init {
      Disposer.register(preview) { _preview = null }
    }

    override fun createViewer(project: Project): DiffEditorViewer {
      val preview = _preview ?: throw IllegalArgumentException("Preview already disposed")
      val editorViewer = preview.createViewer()
      Disposer.register(preview, editorViewer.disposable)
      return editorViewer
    }

    override fun getEditorTabName(project: Project, editors: List<FileEditor>): String {
      val editor = editors.filterIsInstance<DiffEditorViewerFileEditor>().firstOrNull()
      return _preview?.getEditorTabName(editor?.editorViewer)
             ?: DiffBundle.message("label.default.diff.editor.tab.name")
    }

    override fun isValid(): Boolean {
      return _preview != null
    }
  }
}
