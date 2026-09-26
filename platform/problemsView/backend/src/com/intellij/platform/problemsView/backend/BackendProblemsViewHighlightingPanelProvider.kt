// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.problemsView.backend

import com.intellij.analysis.problemsView.toolWindow.ProblemsView
import com.intellij.analysis.problemsView.toolWindow.ProblemsViewBundle
import com.intellij.analysis.problemsView.toolWindow.ProblemsViewPanel
import com.intellij.analysis.problemsView.toolWindow.ProblemsViewPanelProvider
import com.intellij.analysis.problemsView.toolWindow.ProblemsViewState
import com.intellij.analysis.problemsView.toolWindow.ProblemsViewTab
import com.intellij.openapi.project.Project
import com.intellij.analysis.problemsView.toolWindow.splitApi.isSplitProblemsViewKeyEnabled
import com.intellij.openapi.util.Disposer
import com.intellij.util.ui.UIUtil

/**
 * Used to create a mock highlighting panel, which will be replaced in frontend.
 * This provider needs to exist in the backend, since the entire toolwindow is created in the backend,
 * and we cannot move it to frontend since it would break compatibility with other not-split usages.
 */
internal class BackendProblemsViewHighlightingPanelProvider(private val project: Project) : ProblemsViewPanelProvider {

  override fun create(): ProblemsViewTab? {
    if (!isSplitProblemsViewKeyEnabled()) {
      return null
    }

    val toolWindow = ProblemsView.getToolWindow(project) ?: return null
    val mockPanel = MockHighlightingPanel(project, ProblemsViewState.getInstance(project))

    Disposer.register(toolWindow.disposable, mockPanel)

    return mockPanel
  }
}

internal class MockHighlightingPanel(project: Project, state: ProblemsViewState)
  : ProblemsViewPanel(project, "BECurrentFile", state, ProblemsViewBundle.messagePointer("problems.view.highlighting")) {

  init {
    UIUtil.markAsShowing(this, true)
  }

}
