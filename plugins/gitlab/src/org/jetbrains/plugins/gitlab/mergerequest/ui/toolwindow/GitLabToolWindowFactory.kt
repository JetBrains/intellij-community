// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.toolwindow

import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.ui.toolwindow.dontHideOnEmptyContent
import com.intellij.collaboration.ui.toolwindow.manageReviewToolwindowTabs
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.impl.content.ToolWindowContentUi
import com.intellij.util.cancelOnDispose
import com.intellij.util.childScope
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import org.jetbrains.plugins.gitlab.mergerequest.ui.GitLabToolWindowViewModel
import org.jetbrains.plugins.gitlab.util.GitLabBundle

internal class GitLabToolWindowFactory : ToolWindowFactory, DumbAware {
  override fun init(toolWindow: ToolWindow) =
    toolWindow.project.service<GitLabMergeRequestsToolWindowController>().manageToolWindow(toolWindow)

  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) =
    project.service<GitLabMergeRequestsToolWindowController>().manageContent(toolWindow)

  override fun shouldBeAvailable(project: Project): Boolean = false

  companion object {
    const val ID = "Merge Requests"
  }
}

@Service(Service.Level.PROJECT)
private class GitLabMergeRequestsToolWindowController(private val project: Project, parentCs: CoroutineScope) {
  private val cs = parentCs.childScope(Dispatchers.Main)

  @RequiresEdt
  fun manageToolWindow(toolWindow: ToolWindow) {
    cs.launch {
      val vm = project.serviceAsync<GitLabToolWindowViewModel>()
      launchNow {
        vm.isAvailable.collect {
          toolWindow.isAvailable = it
        }
      }
      launchNow {
        vm.activationRequests.collect {
          toolWindow.activate {}
        }
      }
    }.cancelOnDispose(toolWindow.disposable)
  }

  @RequiresEdt
  fun manageContent(toolWindow: ToolWindow) {
    toolWindow.component.putClientProperty(ToolWindowContentUi.HIDE_ID_LABEL, "true")
    toolWindow.dontHideOnEmptyContent()

    cs.launch {
      val vm = project.serviceAsync<GitLabToolWindowViewModel>()
      val componentFactory = GitLabReviewTabComponentFactory(project, vm)

      manageReviewToolwindowTabs(cs, toolWindow, vm, componentFactory, GitLabBundle.message("merge.request.toolwindow.tab.title"))

      toolWindow.setAdditionalGearActions(DefaultActionGroup(GitLabSwitchProjectAndAccountAction()))
      awaitCancellation()
    }.cancelOnDispose(toolWindow.contentManager)
  }
}