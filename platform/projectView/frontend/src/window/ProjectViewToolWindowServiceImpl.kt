// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.frontend.window

import com.intellij.openapi.application.UI
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowContentUiType
import com.intellij.platform.project.projectId
import com.intellij.platform.projectView.frontend.pane.FrontendProjectViewPane
import com.intellij.platform.projectView.frontend.pane.FrontendProjectViewPaneProviderEP
import com.intellij.platform.projectView.pane.ProjectViewPaneProviderId
import com.intellij.platform.projectView.pane.projectViewPaneId
import com.intellij.platform.projectView.rpc.ProjectViewRpc
import com.intellij.platform.projectView.window.ProjectViewToolWindowService
import com.intellij.ui.content.ContentFactory
import kotlinx.coroutines.*

internal class ProjectViewToolWindowServiceImpl(
  val project: Project,
) : ProjectViewToolWindowService {
  override fun setupToolWindow(toolWindow: ToolWindow) {
    toolWindow.setContentUiType(ToolWindowContentUiType.COMBO, null)
  }

  override suspend fun manageToolWindow(toolWindow: ToolWindow) {
    supervisorScope {
      for (provider in FrontendProjectViewPaneProviderEP.extensionList) {
        for (paneId in listOf(projectViewPaneId("ProjectPane"))) {
          val pane = withContext(Dispatchers.UI) {
            provider.createPane(paneId)
          }
          launch(CoroutineName("Manage PV pane ${pane.id} from the provider ${provider.id}")) {
            managePane(toolWindow, provider.id, pane)
          }
        }
      }
    }
  }

  suspend fun managePane(toolWindow: ToolWindow, providerId: ProjectViewPaneProviderId, pane: FrontendProjectViewPane) {
    coroutineScope {
      withContext(Dispatchers.UI) {
        val content = ContentFactory.getInstance().createContent(
          /* component = */ pane.component,
          /* displayName = */ pane.displayName,
          /* isLockable = */ false
        )
        toolWindow.contentManager.addContent(content)
      }
      val rpc = ProjectViewRpc.getInstance()
      launch(CoroutineName("Pane $providerId:${pane.id} state updates")) {
        rpc.getPaneStateFlow(toolWindow.project.projectId(), providerId, pane.id).collect { event ->
          withContext(Dispatchers.UI) {
            pane.applyStateChange(event)
          }
        }
      }
      launch(CoroutineName("Pane $providerId:${pane.id} requests to the backend")) {
        val rpcChannel = rpc.getPaneRequestChannel(project.projectId(), providerId, pane.id)
        for (request in pane.requestChannel) {
          rpcChannel.send(request)
        }
      }
    }
  }
}
