// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.problemsView.frontend

import com.intellij.analysis.problemsView.toolWindow.ProblemsViewBundle
import com.intellij.analysis.problemsView.toolWindow.ProblemsViewPanel
import com.intellij.analysis.problemsView.toolWindow.ProblemsViewState
import com.intellij.analysis.problemsView.toolWindow.splitApi.actions.setupEmptyStatusActions
import com.intellij.openapi.project.Project

internal class FrontendProjectErrorsPanel(project: Project, state: ProblemsViewState)
  : ProblemsViewPanel(project,
                      "ProjectErrors",
                      state,
                      ProblemsViewBundle.messagePointer("problems.view.project")) {

  init {
    super.init()

    setupEmptyPanel()
    this.treeModel.root = FrontendProjectErrorsRootProvider.getInstance(project).createRoot(this)
    updateToolWindowContent()
  }

  private fun setupEmptyPanel() {
    val status = this.tree.emptyText
    status.text = ProblemsViewBundle.message("problems.view.project.empty")
    setupEmptyStatusActions(status, this)
  }

  override fun getPopupHandlerGroupId(): String {
    return "ProblemsView.Frontend.TreePopup"
  }

  override fun getToolbarActionGroupId(): String {
    return "ProblemsView.Frontend.Toolbar"
  }
}
