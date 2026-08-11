// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.analysis.problemsView.toolWindow

import com.intellij.analysis.problemsView.toolWindow.splitApi.actions.setupEmptyStatusActions
import com.intellij.analysis.problemsView.toolWindow.splitApi.isSplitProblemsViewKeyEnabled
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class ProblemsViewProjectErrorsPanelProvider(private val project: Project) : ProblemsViewPanelProvider {

  override fun create(): ProblemsViewTab? {
    if (isSplitProblemsViewKeyEnabled()) return null

    val state = ProblemsViewState.getInstance(project)
    val panel = ProblemsViewPanel(project,
                                  "ProjectErrors",
                                  state,
                                  ProblemsViewBundle.messagePointer("problems.view.project"))
    panel.treeModel.root = CollectorBasedRoot(panel)

    val status = panel.tree.emptyText
    status.text = ProblemsViewBundle.message("problems.view.project.empty")
    setupEmptyStatusActions(status, panel)

    return panel
  }
}