// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.toolwindow

import com.intellij.collaboration.ui.toolwindow.ReviewToolwindowViewModel
import com.intellij.collaboration.ui.toolwindow.dontHideOnEmptyContent
import com.intellij.collaboration.ui.toolwindow.manageReviewToolwindowTabs
import com.intellij.openapi.actionSystem.CommonShortcuts
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.impl.content.ToolWindowContentUi
import com.intellij.platform.util.coroutines.childScope
import com.intellij.platform.util.coroutines.flow.mapStateIn
import com.intellij.util.cancelOnDispose
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.plugins.gitlab.mergerequest.action.GitLabMergeRequestsActionKeys
import org.jetbrains.plugins.gitlab.mergerequest.ui.GitLabProjectViewModel
import org.jetbrains.plugins.gitlab.mergerequest.ui.toolwindow.model.GitLabToolWindowConnectedProjectViewModel
import org.jetbrains.plugins.gitlab.util.GitLabBundle

internal class GitLabToolWindowFactory : ToolWindowFactory, DumbAware {
  override suspend fun manage(toolWindow: ToolWindow, toolWindowManager: ToolWindowManager) {
    toolWindow.project.serviceAsync<GitLabMergeRequestsToolWindowController>().manageToolWindow(toolWindow)
  }

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

  suspend fun manageToolWindow(toolWindow: ToolWindow) {
    val vm = project.serviceAsync<GitLabProjectViewModel>()
    coroutineScope {
      launch {
        vm.isAvailable.collect {
          withContext(Dispatchers.EDT) {
            toolWindow.isAvailable = it
          }
        }
      }
      launch {
        vm.activationRequests.collect {
          withContext(Dispatchers.EDT) {
            toolWindow.activate(null)
          }
        }
      }
    }
  }

  @RequiresEdt
  fun manageContent(toolWindow: ToolWindow) {
    toolWindow.component.putClientProperty(ToolWindowContentUi.HIDE_ID_LABEL, "true")
    toolWindow.dontHideOnEmptyContent()

    val wrapper = ActionUtil.wrap("GitLab.Merge.Request.Create")
    wrapper.registerCustomShortcutSet(CommonShortcuts.getNew(), toolWindow.component)
    toolWindow.setTitleActions(listOf(wrapper))
    toolWindow.setAdditionalGearActions(DefaultActionGroup(GitLabSwitchProjectAndAccountAction()))

    cs.launch {
      val vm = project.serviceAsync<GitLabProjectViewModel>()
      val componentFactory = GitLabReviewTabComponentFactory(project, vm)
      toolWindow.contentManager.addUiDataProvider { sink ->
        sink[GitLabMergeRequestsActionKeys.PROJECT_VM] = vm
        sink[GitLabMergeRequestsActionKeys.CONNECTED_PROJECT_VM] = vm.connectedProjectVm.value
      }

      val reviewToolWindowVm = object : ReviewToolwindowViewModel<GitLabToolWindowConnectedProjectViewModel> {
        override val projectVm: StateFlow<GitLabToolWindowConnectedProjectViewModel?> = vm.connectedProjectVm.mapStateIn(cs, SharingStarted.Eagerly) {
          it as GitLabToolWindowConnectedProjectViewModel?
        }
      }
      manageReviewToolwindowTabs(this, toolWindow, reviewToolWindowVm, componentFactory, GitLabBundle.message("merge.request.toolwindow.tab.title"))
      awaitCancellation()
    }.cancelOnDispose(toolWindow.contentManager)
  }
}