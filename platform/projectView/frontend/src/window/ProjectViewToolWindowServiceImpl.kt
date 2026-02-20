@file:OptIn(ExperimentalAtomicApi::class, AwaitCancellationAndInvoke::class)
// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.frontend.window

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.UI
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.rethrowControlFlowException
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowContentUiType
import com.intellij.openapi.wm.impl.content.ToolWindowContentUi
import com.intellij.platform.project.projectId
import com.intellij.platform.projectView.frontend.pane.FrontendProjectViewPane
import com.intellij.platform.projectView.frontend.pane.FrontendProjectViewPaneProviderEP
import com.intellij.platform.projectView.pane.ProjectViewPaneId
import com.intellij.platform.projectView.pane.ProjectViewPaneProviderId
import com.intellij.platform.projectView.pane.projectViewPaneId
import com.intellij.platform.projectView.pane.projectViewPaneProviderId
import com.intellij.platform.projectView.rpc.ProjectViewRpc
import com.intellij.platform.projectView.window.ProjectViewOptionSupport
import com.intellij.platform.projectView.window.ProjectViewToolWindowService
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.content.ContentManagerEvent
import com.intellij.ui.content.ContentManagerListener
import com.intellij.util.AwaitCancellationAndInvoke
import com.intellij.util.awaitCancellationAndInvoke
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.launchOnShow
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.jdom.Element
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.time.Duration.Companion.seconds

@State(name = "FrontendProjectView", storages = [Storage(StoragePathMacros.PRODUCT_WORKSPACE_FILE)])
internal class ProjectViewToolWindowServiceImpl(
  val project: Project,
) : ProjectViewToolWindowService, PersistentStateComponent<Element> {
  private val actionGroup: DefaultActionGroup by lazy { DefaultActionGroup() }
  private val stateInitJob = CompletableDeferred<Unit>()
  private val state = ConcurrentHashMap<ProjectViewPaneProviderId, ConcurrentHashMap<ProjectViewPaneId, Element>>()
  private val currentPane = AtomicReference<FrontendProjectViewPane?>(null)
  private val currentPaneListener: ContentManagerListener = object : ContentManagerListener {
    override fun selectionChanged(event: ContentManagerEvent) {
      if (event.operation == ContentManagerEvent.ContentOperation.add) {
        val newPane = event.content.getUserData(PANE_KEY)
        val oldPane = currentPane.exchange(newPane)
        currentPaneChanged(oldPane, newPane)
      }
    }
  }
  private val optionService = ProjectViewOptionSupportImpl(currentPane)

  override fun getOptionSupport(): ProjectViewOptionSupport = optionService

  @RequiresEdt
  override fun setupToolWindow(toolWindow: ToolWindow) {
    toolWindow.setDefaultContentUiType(ToolWindowContentUiType.COMBO)
    toolWindow.setAdditionalGearActions(actionGroup)
    toolWindow.component.putClientProperty(ToolWindowContentUi.HIDE_ID_LABEL, "true")
  }

  override suspend fun manageToolWindow(toolWindow: ToolWindow) {
    supervisorScope {
      for (provider in FrontendProjectViewPaneProviderEP.extensionList) {
        for (paneId in listOf(projectViewPaneId("ProjectPane"))) {
          launch(CoroutineName("Manage PV pane $paneId from the provider ${provider.id}")) {
            try {
              LOG.debug { "Initializing pane $paneId" }
              val pane = withContext(Dispatchers.UI) {
                provider.createPane(project, paneId)
              }
              LOG.debug { "Created pane $paneId" }
              managePane(toolWindow, provider.id, pane)
            }
            catch (e: Throwable) {
              rethrowControlFlowException(e)
              LOG.error("Failed to initialize pane $paneId", e)
            }
          }
        }
      }
    }
  }

  private fun currentPaneChanged(
    oldPane: FrontendProjectViewPane?,
    newPane: FrontendProjectViewPane?,
  ) {
    LOG.debug { "The project view pane changed: ${oldPane?.id} -> ${newPane?.id}" }
    updateToolbarActions()
  }

  private fun updateToolbarActions() {
    actionGroup.removeAll()
    val group = ActionManager.getInstance().getAction("ProjectView.ToolWindow.SecondaryActions") as DefaultActionGroup
    for (action in group.getChildActionsOrStubs()) {
      actionGroup.addAction(action).setAsSecondary(true)
    }
  }

  suspend fun managePane(toolWindow: ToolWindow, providerId: ProjectViewPaneProviderId, pane: FrontendProjectViewPane) {
    coroutineScope {
      val paneScope = this
      withTimeoutOrNull(15.seconds) { // in case something went wrong with loading the state
        stateInitJob.await()
      }
      LOG.debug { "The saved state has been loaded for ${pane.id}" }
      try {
        withContext(Dispatchers.UI) {
          val content = ContentFactory.getInstance().createContent(
            /* component = */ pane.component,
            /* displayName = */ pane.displayName,
            /* isLockable = */ false
          )
          content.putUserData(PANE_KEY, pane)
          toolWindow.contentManager.addContentManagerListener(currentPaneListener)
          // It's important to use the right scope here, or else the current scope (withContext) would be blocked.
          paneScope.awaitCancellationAndInvoke { 
            toolWindow.contentManager.removeContentManagerListener(currentPaneListener)
          }
          toolWindow.contentManager.addContent(content)
          LOG.debug { "The content has been created for ${pane.id}" }
          pane.component.launchOnShow("Pane $providerId:${pane.id} service state saving/restoring") {
            try {
              val paneState = state[providerId]?.get(pane.id)
              if (paneState != null) {
                pane.restoreStateFrom(paneState)
              }
              LOG.debug { "Applied the loaded state for ${pane.id}" }
              awaitCancellation()
            }
            finally {
              val paneElement = Element("pane")
              paneElement.setAttribute("provider", providerId.idString)
              paneElement.setAttribute("pane", pane.id.idString)
              pane.saveStateTo(paneElement)
              state.computeIfAbsent(providerId) { ConcurrentHashMap() }[pane.id] = paneElement
              LOG.debug { "Saved the last state for ${pane.id}" }
            }
          }
        }
      }
      catch (e: Throwable) {
        LOG.debug(e)
        throw e
      }
      LOG.debug { "Obtaining the RCP service to manage the pane ${pane.id}" }
      val rpc = ProjectViewRpc.getInstance()
      launch(CoroutineName("Pane $providerId:${pane.id} state updates")) {
        LOG.debug { "Collecting pane state updates for ${pane.id}" }
        rpc.getPaneStateFlow(toolWindow.project.projectId(), providerId, pane.id).collect { eventDTO ->
          withContext(Dispatchers.UI) {
            val event = eventDTO.toEvent()
            LOG.trace { "Update pane state for ${pane.id}: $event" }
            pane.applyStateChange(event)
          }
        }
      }
      launch(CoroutineName("Pane $providerId:${pane.id} requests to the backend")) {
        LOG.debug { "Sending pane requests for ${pane.id}" }
        val rpcChannel = rpc.getPaneRequestChannel(project.projectId(), providerId, pane.id)
        for (request in pane.requestChannel) {
          LOG.trace { "Sent request for pane ${pane.id}: $request" }
          rpcChannel.send(request)
        }
      }
      LOG.debug { "Managing pane ${pane.id}" }
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

private val PANE_KEY = Key.create<FrontendProjectViewPane>("FrontendProjectViewPane")
private val LOG = logger<ProjectViewToolWindowServiceImpl>()
