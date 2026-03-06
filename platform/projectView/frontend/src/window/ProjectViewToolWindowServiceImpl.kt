@file:OptIn(ExperimentalAtomicApi::class, AwaitCancellationAndInvoke::class)
// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.frontend.window

import com.intellij.ide.projectView.impl.ProjectViewPane
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
import com.intellij.platform.projectView.actions.ProjectViewActionSupport
import com.intellij.platform.projectView.frontend.actions.ProjectViewActionSupportImpl
import com.intellij.platform.projectView.frontend.pane.FrontendProjectViewPane
import com.intellij.platform.projectView.frontend.pane.FrontendProjectViewPaneProviderEP
import com.intellij.platform.projectView.pane.ProjectViewPaneId
import com.intellij.platform.projectView.pane.projectViewPaneId
import com.intellij.platform.projectView.rpc.ProjectViewRpc
import com.intellij.platform.projectView.window.ProjectViewToolWindowService
import com.intellij.ui.content.Content
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
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
  private val menuActionGroup: DefaultActionGroup by lazy { DefaultActionGroup() }
  private val stateInitJob = CompletableDeferred<Unit>()
  private lateinit var defaultSelection: ProjectViewPaneId
  private val paneSelectJob = CompletableDeferred<Unit>()
  private val persistentState = ProjectViewToolWindowServiceState()
  private val currentPaneFlow = MutableStateFlow<FrontendProjectViewPane?>(null)
  private val currentPaneListener: ContentManagerListener = object : ContentManagerListener {
    override fun selectionChanged(event: ContentManagerEvent) {
      if (event.operation == ContentManagerEvent.ContentOperation.add) {
        val newPane = event.content.getUserData(PANE_KEY)
        val oldPane = currentPaneFlow.value
        currentPaneFlow.value = newPane
        currentPaneChanged(oldPane, newPane)
      }
    }
  }
  private val optionService = ProjectViewActionSupportImpl(currentPaneFlow)

  override fun getActionSupport(): ProjectViewActionSupport = optionService

  @RequiresEdt
  override fun setupToolWindow(toolWindow: ToolWindow) {
    toolWindow.setDefaultContentUiType(ToolWindowContentUiType.COMBO)
    val action = ActionManager.getInstance().getAction("ProjectViewToolbar")
    if (action != null) toolWindow.setTitleActions(listOf(action))
    toolWindow.setAdditionalGearActions(menuActionGroup)
    toolWindow.component.putClientProperty(ToolWindowContentUi.HIDE_ID_LABEL, "true")
  }

  override suspend fun manageToolWindow(toolWindow: ToolWindow) {
    supervisorScope {
      launch(Dispatchers.UI + CoroutineName("Current pane listener")) {
        toolWindow.contentManager.addContentManagerListener(currentPaneListener)
        // It's important to use the right scope here, or else the current scope (withContext) would be blocked.
        awaitCancellationAndInvoke {
          toolWindow.contentManager.removeContentManagerListener(currentPaneListener)
        }
      }
      val rpc = ProjectViewRpc.getInstance()
      for (provider in FrontendProjectViewPaneProviderEP.extensionList) {
        val paneDescriptors = rpc.getPaneDescriptors(project.projectId())
        defaultSelection = paneDescriptors.firstOrNull { it.isDefault }?.id ?: defaultSelectedPaneId()
        for (descriptor in paneDescriptors) {
          launch(CoroutineName("Manage PV pane ${descriptor.id} from the provider ${provider}")) {
            try {
              LOG.debug { "Initializing pane ${descriptor.id}" }
              val pane = withContext(Dispatchers.UI) {
                provider.createPane(project, descriptor)
              }
              LOG.debug { "Created pane ${descriptor.id}" }
              managePane(toolWindow, pane)
            }
            catch (e: Throwable) {
              rethrowControlFlowException(e)
              LOG.error("Failed to initialize pane ${descriptor.id}", e)
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
    oldPane?.isCurrent = false
    newPane?.isCurrent = true
    if (newPane != null) {
      persistentState.putSelectedPaneState(newPane.id)
    }
    updateMenuActions()
  }

  private fun updateMenuActions() {
    menuActionGroup.removeAll()
    val group = ActionManager.getInstance().getAction("ProjectView.ToolWindow.SecondaryActions") as DefaultActionGroup
    for (action in group.getChildActionsOrStubs()) {
      menuActionGroup.addAction(action).setAsSecondary(true)
    }
  }

  suspend fun managePane(toolWindow: ToolWindow, pane: FrontendProjectViewPane) {
    coroutineScope {
      withTimeoutOrNull(15.seconds) { // in case something went wrong with loading the state
        stateInitJob.await()
      }
      LOG.debug { "The saved state has been loaded for ${pane.id}" }
      launch(Dispatchers.UI + CoroutineName("Manage TW content for PV pane ${pane.id}")) {
        pane.component.launchOnShow("Pane ${pane.id} service state saving/restoring") {
          try {
            val paneState = persistentState.getPaneState(pane.id)
            if (paneState != null) {
              pane.restoreStateFrom(paneState)
            }
            LOG.debug { "Applied the loaded state for ${pane.id}" }
            awaitCancellation()
          }
          finally {
            val paneElement = Element("pane")
            paneElement.setAttribute("pane", pane.id.idString)
            pane.saveStateTo(paneElement)
            persistentState.putPaneState(pane.id, paneElement)
            LOG.debug { "Saved the last state for ${pane.id}" }
          }
        }
        val selectedPaneId = persistentState.getSelectedPaneState() ?: defaultSelection
        val mustSelectThisPane = selectedPaneId == pane.id
        if (!mustSelectThisPane) {
          withTimeoutOrNull(5.seconds) { // at this point the state is already received, and panes are added very quickly
            paneSelectJob.await()
          }
        }
        val content = addContent(toolWindow, pane)
        if (mustSelectThisPane) {
          paneSelectJob.complete(Unit)
        }
        LOG.debug { "The content has been created for ${pane.id}" }
        try {
          awaitCancellation()
        }
        finally {
          removeContent(toolWindow, content)
        }
      }
      LOG.debug { "Obtaining the RCP service to manage the pane ${pane.id}" }
      val rpc = ProjectViewRpc.getInstance()
      launch(CoroutineName("Pane ${pane.id} state updates")) {
        currentPaneFlow.collectLatest { currentPane ->
          if (currentPane == pane) {
            LOG.debug { "The pane ${pane.id} is selected, starting to collect its updates"}
            try {
              rpc.getPaneStateFlow(toolWindow.project.projectId(), pane.id).collect { eventDTO ->
                withContext(Dispatchers.UI) {
                  val event = eventDTO.toEvent()
                  LOG.trace { "Update pane state for ${pane.id}: $event" }
                  pane.applyStateChange(event)
                }
              }
            }
            finally {
              LOG.debug { "The pane ${pane.id} has finished collecting its updates"}
            }
          }
        }
      }
      launch(CoroutineName("Pane ${pane.id} requests to the backend")) {
        LOG.debug { "Sending pane requests for ${pane.id}" }
        val rpcChannel = rpc.getPaneRequestChannel(project.projectId(), pane.id)
        for (request in pane.requestChannel) {
          LOG.trace { "Sent request for pane ${pane.id}: $request" }
          rpcChannel.send(request)
        }
      }
      LOG.debug { "Managing pane ${pane.id}" }
      pane.manage()
    }
  }

  @RequiresEdt
  private fun addContent(
    toolWindow: ToolWindow,
    newPane: FrontendProjectViewPane,
  ): Content {
    val newContent = ContentFactory.getInstance().createContent(
      /* component = */ newPane.component,
      /* displayName = */ newPane.displayName,
      /* isLockable = */ false
    )
    newContent.putUserData(PANE_KEY, newPane)
    val contentManager = toolWindow.contentManager
    val bisectIndex = (0 until contentManager.contentCount).toList().binarySearch { i ->
      val existingContent = contentManager.getContent(i)
      val existingPane = existingContent?.getUserData(PANE_KEY) ?: return@binarySearch 1 // non-pane contents last (not really supported)
      val compareOrder = existingPane.order.compareTo(newPane.order)
      if (compareOrder == 0) {
        existingPane.id.compareTo(newPane.id)
      }
      else {
        compareOrder
      }
    }
    val index = if (bisectIndex >= 0) {
      LOG.warn("We have two panes with the same order and ID, shouldn't be possible: " +
               "$newPane and ${contentManager.getContent(bisectIndex)?.getUserData(PANE_KEY)}")
      bisectIndex + 1 // add after the dup pane
    }
    else {
      -bisectIndex - 1
    }
    contentManager.addContent(newContent, index)
    return newContent
  }

  @RequiresEdt
  private fun removeContent(toolWindow: ToolWindow, content: Content) {
    toolWindow.contentManager.removeContent(content, true)
  }

  override fun getState(): Element = Element("projectView").also { element ->
    persistentState.writeStateTo(element)
  }

  override fun noStateLoaded() {
    loadState(Element("projectView"))
  }

  override fun loadState(state: Element) {
    persistentState.readStateFrom(state)
    stateInitJob.complete(Unit)
  }
}

private class ProjectViewToolWindowServiceState {
  private val selectedPaneState = AtomicReference<ProjectViewPaneId?>(null)
  private val paneState = ConcurrentHashMap<ProjectViewPaneId, Element>()

  fun getSelectedPaneState(): ProjectViewPaneId? {
    return selectedPaneState.load()
  }

  fun putSelectedPaneState(state: ProjectViewPaneId?) {
    selectedPaneState.store(state)
  }

  fun getPaneState(paneId: ProjectViewPaneId): Element? {
    return paneState[paneId]
  }

  fun putPaneState(paneId: ProjectViewPaneId, state: Element) {
    paneState[paneId] = state
  }

  fun writeStateTo(element: Element) {
    writeSelectedPane(element)
    writePanes(element)
  }

  private fun writeSelectedPane(element: Element) {
    val selectedPane = selectedPaneState.load()
    if (selectedPane != null) {
      element.setAttribute("selectedPaneId", selectedPane.idString)
    }
  }

  private fun writePanes(element: Element) {
    val panesElement = Element("panes")
    for (paneState in paneState.values) {
      panesElement.addContent(paneState.clone())
    }
    element.addContent(panesElement)
  }

  fun readStateFrom(element: Element) {
    readSelectedPane(element)
    readPanes(element)
  }

  private fun readSelectedPane(element: Element) {
    val selectedPaneId = element.getAttributeValue("selectedPaneId")?.let { projectViewPaneId(it) }
    if (selectedPaneId != null) {
      selectedPaneState.store(selectedPaneId)
    }
  }

  private fun readPanes(element: Element) {
    val panesElement = element.getChild("panes")
    for (paneElement in panesElement?.getChildren("pane") ?: emptyList()) {
      val paneIdString = paneElement.getAttribute("pane")?.value ?: continue
      val paneId = projectViewPaneId(paneIdString)
      paneState[paneId] = paneElement
    }
  }
}

private fun defaultSelectedPaneId(): ProjectViewPaneId =
  projectViewPaneId(ProjectViewPane.ID)

private val PANE_KEY = Key.create<FrontendProjectViewPane>("FrontendProjectViewPane")
private val LOG = logger<ProjectViewToolWindowServiceImpl>()
