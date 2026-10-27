// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.problemView.security.backend

import com.intellij.analysis.problemsView.toolWindow.ProblemsView
import com.intellij.analysis.problemsView.toolWindow.ProblemsViewPanelProvider
import com.intellij.analysis.problemsView.toolWindow.ProblemsViewTab
import com.intellij.analysis.problemsView.toolWindow.ProblemsViewToolWindowUtils
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * The Problems View tab that hosts the security features. It appears only while at least one
 * [ProblemsViewSubTabProvider] contributes to [ProblemsViewSubTabs.SECURITY_TAB_ID].
 */
internal class SecurityProblemsViewTab(project: Project) : SubTabbedProblemsViewTab(
  project = project,
  hostTabId = ProblemsViewSubTabs.SECURITY_TAB_ID,
  tabTitle = SecurityProblemsViewBundle.message("security.problems.view.tab.name"),
  usagesTabId = "Security",
)

// the backend owns the Problems tool window in split mode (IJPL-223406)
@Suppress("SplitModeApiUsage")
internal class SecurityProblemsViewPanelProvider(private val project: Project) : ProblemsViewPanelProvider {
  override fun create(): ProblemsViewTab? {
    // a plugin can add a provider later, so the watch starts here
    project.service<SecurityProblemsViewTabWatcher>()
    return if (SubTabbedProblemsViewTab.hasSubTabs(ProblemsViewSubTabs.SECURITY_TAB_ID)) {
      SecurityProblemsViewTab(project)
    }
    else null
  }
}

@Service(Service.Level.PROJECT)
internal class SecurityProblemsViewTabWatcher(private val project: Project, private val scope: CoroutineScope) {
  init {
    // the platform changes the extension point off the EDT, and the rebuild is Swing work
    ProblemsViewSubTabProvider.EP.addChangeListener(scope) {
      scope.launch(Dispatchers.EDT) { rebuildTab() }
    }
  }

  private fun rebuildTab() {
    if (project.isDisposed) return
    val window = ProblemsView.getToolWindow(project) ?: return
    val manager = window.contentManagerIfCreated ?: return
    val content = ProblemsViewToolWindowUtils.getContentById(project, ProblemsViewSubTabs.SECURITY_TAB_ID)
    val wasSelected = content != null && content == manager.selectedContent
    ProblemsViewToolWindowUtils.removeTab(project, ProblemsViewSubTabs.SECURITY_TAB_ID)
    ProblemsViewToolWindowUtils.addTab(project, SecurityProblemsViewPanelProvider(project))
    if (wasSelected) {
      ProblemsViewToolWindowUtils.selectContent(manager, ProblemsViewSubTabs.SECURITY_TAB_ID)
    }
  }
}
