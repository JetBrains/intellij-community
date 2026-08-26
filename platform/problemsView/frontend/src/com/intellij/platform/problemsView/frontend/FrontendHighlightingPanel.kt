// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.problemsView.frontend

import com.intellij.analysis.problemsView.toolWindow.HighlightingPanel
import com.intellij.analysis.problemsView.toolWindow.HighlightingProblem
import com.intellij.analysis.problemsView.toolWindow.ProblemNode
import com.intellij.analysis.problemsView.toolWindow.ProblemsViewState
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.ui.tree.TreeUtil

internal class FrontendHighlightingPanel(project: Project, state: ProblemsViewState)
  : HighlightingPanel(project, state) {

  private val quickFixService = FrontendProblemsViewQuickFixService.getInstance(project)

  init {
    tree.addTreeSelectionListener {
      val problem = TreeUtil.getLastUserObject(ProblemNode::class.java, tree.selectionPath)?.problem as? HighlightingProblem
      quickFixService.selectProblem(problem)
    }
  }

  override fun getPopupHandlerGroupId(): String = "ProblemsView.Frontend.TreePopup"

  override fun getToolbarActionGroupId(): String = "ProblemsView.Frontend.Toolbar"

  override fun setCurrentFile(virtualFile: VirtualFile?, document: Document?) {
    if (virtualFile != getCurrentFile()) {
      quickFixService.discardQuickFixes()
    }
    super.setCurrentFile(virtualFile, document)
  }

  override fun dispose() {
    quickFixService.discardQuickFixes()
    super.dispose()
  }
}
