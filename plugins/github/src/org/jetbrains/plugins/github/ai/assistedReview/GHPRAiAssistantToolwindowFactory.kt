// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.ai.assistedReview

import com.intellij.collaboration.ui.toolwindow.dontHideOnEmptyContent
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
import com.intellij.ui.content.ContentFactory
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.*
import org.jetbrains.plugins.github.ai.GithubAIBundle

internal class GHPRAiAssistantToolwindowFactory : ToolWindowFactory, DumbAware {
  override fun init(toolWindow: ToolWindow) {
    toolWindow.setStripeShortTitleProvider(GithubAIBundle.messagePointer("tab.title.pr.ai.assistant"))
  }

  override suspend fun manage(toolWindow: ToolWindow, toolWindowManager: ToolWindowManager) {
    toolWindow.project.serviceAsync<GHPRAiAssistantToolwindowController>().manageIconInToolbar(toolWindow)
  }

  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    project.service<GHPRAiAssistantToolwindowController>().manageContent(toolWindow)
  }

  override fun shouldBeAvailable(project: Project): Boolean = true
}


@Service(Service.Level.PROJECT)
private class GHPRAiAssistantToolwindowController(private val project: Project, parentCs: CoroutineScope) {
  private val cs = parentCs.childScope(Dispatchers.Main)

  suspend fun manageIconInToolbar(toolWindow: ToolWindow) {
    coroutineScope {
      val vm = project.serviceAsync<GHPRAiAssistantToolwindowViewModel>()

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

    // so it's not closed when all content is removed
    cs.launch {
      val vm = project.serviceAsync<GHPRAiAssistantToolwindowViewModel>()
      toolWindow.dontHideOnEmptyContent()
      val displayName = "Review AI Assistant"
      val component = GHPRAiAssistantToolwindowComponentFactory.create(cs, vm)
      toolWindow.contentManager.addContent(ContentFactory.getInstance().createContent(component, displayName, false))
    }
  }
}