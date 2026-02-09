// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.frontend.window

import com.intellij.openapi.application.UI
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowContentUiType
import com.intellij.platform.project.projectId
import com.intellij.platform.projectView.frontend.pane.FrontendProjectViewPane
import com.intellij.platform.projectView.frontend.pane.FrontendProjectViewPaneProviderEP
import com.intellij.platform.projectView.pane.ProjectViewPaneId
import com.intellij.platform.projectView.pane.ProjectViewPaneProviderId
import com.intellij.platform.projectView.pane.projectViewPaneId
import com.intellij.platform.projectView.pane.projectViewPaneProviderId
import com.intellij.platform.projectView.rpc.ProjectViewRpc
import com.intellij.platform.projectView.window.ProjectViewToolWindowService
import com.intellij.ui.content.ContentFactory
import com.intellij.util.ui.launchOnShow
import kotlinx.coroutines.*
import org.jdom.Element
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.seconds

@State(name = "FrontendProjectView", storages = [Storage(StoragePathMacros.PRODUCT_WORKSPACE_FILE)])
internal class ProjectViewToolWindowServiceImpl(
  val project: Project,
) : ProjectViewToolWindowService, PersistentStateComponent<Element> {
  private val stateInitJob = CompletableDeferred<Unit>()
  private val state = ConcurrentHashMap<ProjectViewPaneProviderId, ConcurrentHashMap<ProjectViewPaneId, Element>>()
  
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
      withTimeoutOrNull(15.seconds) { // in case something went wrong with loading the state
        stateInitJob.await()
      }
      withContext(Dispatchers.UI) {
        val content = ContentFactory.getInstance().createContent(
          /* component = */ pane.component,
          /* displayName = */ pane.displayName,
          /* isLockable = */ false
        )
        toolWindow.contentManager.addContent(content)
        pane.component.launchOnShow("Pane $providerId:${pane.id} service state saving/restoring") {
          try {
            val paneState = state[providerId]?.get(pane.id)
            if (paneState != null) {
              pane.restoreStateFrom(paneState)
            }
            awaitCancellation()
          }
          finally {
            val paneElement = Element("pane")
            paneElement.setAttribute("provider", providerId.idString)
            paneElement.setAttribute("pane", pane.id.idString)
            pane.saveStateTo(paneElement)
            state.computeIfAbsent(providerId) { ConcurrentHashMap() }[pane.id] = paneElement
          }
        }
      }
      val rpc = ProjectViewRpc.getInstance()
      launch(CoroutineName("Pane $providerId:${pane.id} state updates")) {
        rpc.getPaneStateFlow(toolWindow.project.projectId(), providerId, pane.id).collect { eventDTO ->
          withContext(Dispatchers.UI) {
            pane.applyStateChange(eventDTO.toEvent())
          }
        }
      }
      launch(CoroutineName("Pane $providerId:${pane.id} requests to the backend")) {
        val rpcChannel = rpc.getPaneRequestChannel(project.projectId(), providerId, pane.id)
        for (request in pane.requestChannel) {
          rpcChannel.send(request)
        }
      }
      pane.manage()
    }
  }

  override fun getState(): Element = Element("projectView").also { element ->
    val panesElement = Element("panes")
    for (providerState in state.values) {
      for (paneState in providerState.values) {
        panesElement.addContent(paneState.clone())
      }
    }
    element.addContent(panesElement)
  }

  override fun noStateLoaded() {
    loadState(Element("projectView"))
  }

  override fun loadState(state: Element) {
    val panesElement = state.getChild("panes")
    for (paneElement in panesElement?.getChildren("pane") ?: emptyList()) {
      val providerIdString = paneElement.getAttribute("provider")?.value ?: continue
      val paneIdString = paneElement.getAttribute("pane")?.value ?: continue
      val providerId = projectViewPaneProviderId(providerIdString)
      val paneId = projectViewPaneId(paneIdString)
      this.state.computeIfAbsent(providerId) { ConcurrentHashMap() }[paneId] = paneElement
    }
    stateInitJob.complete(Unit)
  }
}
