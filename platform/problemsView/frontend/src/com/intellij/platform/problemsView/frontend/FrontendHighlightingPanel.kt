// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.problemsView.frontend

import com.intellij.analysis.problemsView.toolWindow.HighlightingPanel
import com.intellij.analysis.problemsView.toolWindow.ProblemsViewState
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.ui.tree.TreeUtil

internal class FrontendHighlightingPanel(project: Project, state: ProblemsViewState)
  : HighlightingPanel(project, state) {

  override fun getPopupHandlerGroupId(): String {
    return "ProblemsView.Frontend.TreePopup"
  }

  override fun getToolbarActionGroupId(): String {
    return "ProblemsView.Frontend.Toolbar"
  }

  override fun setCurrentFile(virtualFile: VirtualFile?, document: Document?) {
    if (virtualFile == null || document == null) {
      if (treeModel.root == null) return

      val oldRoot = currentRoot
      treeModel.root = null

      (oldRoot as? Disposable)?.let { Disposer.dispose(it) }
    }
    else {
      if (currentRoot?.file == virtualFile) return

      val oldRoot = currentRoot
      treeModel.root = FrontendProblemsViewHighlightingFileRoot(this, virtualFile, document)

      (oldRoot as? Disposable)?.let { Disposer.dispose(it) }
      TreeUtil.promiseSelectFirstLeaf(tree)
    }
    powerSaveStateChanged()
  }
}
