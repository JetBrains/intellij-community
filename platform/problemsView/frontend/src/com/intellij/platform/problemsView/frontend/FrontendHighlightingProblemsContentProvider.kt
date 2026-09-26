// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.problemsView.frontend

import com.intellij.analysis.problemsView.toolWindow.ProblemsViewBundle
import com.intellij.analysis.problemsView.toolWindow.ProblemsViewState
import com.intellij.analysis.problemsView.toolWindow.splitApi.isSplitProblemsViewKeyEnabled
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.ui.content.Content
import org.jetbrains.annotations.Nls

internal class FrontendHighlightingProblemsContentProvider : FrontendProblemsViewContentProvider {
  override fun isAvailable(project: Project): Boolean {
    return isSplitProblemsViewKeyEnabled()
  }

  override fun initTabContent(project: Project, content: Content) {
    thisLogger().debug("initializing highlighting tab content in frontend")
    val panel = FrontendHighlightingPanel(project, ProblemsViewState.getInstance(project))
    content.component = panel
    panel.updateSelectedFile()
  }

  override fun matchesTabName(tabName: String): Boolean {
    val fileTabName = ProblemsViewBundle.messagePointer("problems.view.highlighting").get()
    return unwrapTabName(tabName).startsWith(fileTabName)
  }

  /**
   * The tab name is provided by `com.intellij.analysis.problemsView.toolWindow.ProblemsViewPanel.getName`,
   * which wraps the tab's name in HTML. To correctly match only the File tab, we have to unwrap the tab name.
   */
  @Nls
  private fun unwrapTabName(tabName: String) : String {
    return Regex("""<nobr>(.*?)</nobr>""")
             .find(tabName)
             ?.groupValues
             ?.get(1)
           ?: tabName
  }
}