// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest

import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.ui.toolwindow.dontHideOnEmptyContent
import com.intellij.collaboration.ui.toolwindow.manageReviewToolwindowTabs
import com.intellij.openapi.actionSystem.CommonShortcuts
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.EmptyAction
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
import kotlinx.coroutines.*
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.action.GHPRActionKeys
import org.jetbrains.plugins.github.pullrequest.action.GHPRSelectPullRequestForFileAction
import org.jetbrains.plugins.github.pullrequest.action.GHPRSwitchRemoteAction

internal class GHPRToolWindowFactory : ToolWindowFactory, DumbAware {
  override fun init(toolWindow: ToolWindow) =
    toolWindow.project.service<GHPRToolWindowController>().manageAvailability(toolWindow)

  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) =
    project.service<GHPRToolWindowController>().manageContent(toolWindow)

  override fun shouldBeAvailable(project: Project): Boolean = false
}

@Service(Service.Level.PROJECT)
private class GHPRToolWindowController(private val project: Project, parentCs: CoroutineScope) {
  private val cs = parentCs.childScope(Dispatchers.Main)

  @RequiresEdt
  fun manageAvailability(toolWindow: ToolWindow) {
    cs.launch {
      val vm = project.serviceAsync<GHPRToolWindowViewModel>()
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

    cs.launch {
      val vm = project.serviceAsync<GHPRToolWindowViewModel>()

      coroutineScope {
        toolWindow.contentManager.addDataProvider {
          if (GHPRActionKeys.PULL_REQUESTS_PROJECT_VM.`is`(it)) vm.projectVm.value
          else null
        }

        // so it's not closed when all content is removed
        toolWindow.dontHideOnEmptyContent()
        val componentFactory = GHPRToolWindowTabComponentFactory(project, vm)
        manageReviewToolwindowTabs(this, toolWindow, vm, componentFactory, GithubBundle.message("toolwindow.stripe.Pull_Requests"))

        toolWindow.setTitleActions(listOf(
          EmptyAction.registerWithShortcutSet("Github.Create.Pull.Request", CommonShortcuts.getNew(), toolWindow.component),
          GHPRSelectPullRequestForFileAction(),
        ))
        toolWindow.setAdditionalGearActions(DefaultActionGroup(GHPRSwitchRemoteAction()))

        awaitCancellation()
      }
    }.cancelOnDispose(toolWindow.contentManager)
  }
}