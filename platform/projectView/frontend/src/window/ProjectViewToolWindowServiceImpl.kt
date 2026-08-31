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
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowContentUiType
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.impl.content.ToolWindowContentUi
import com.intellij.platform.projectView.actions.ProjectViewActionSupport
import com.intellij.platform.projectView.frontend.actions.ProjectViewActionSupportImpl
import com.intellij.platform.projectView.frontend.actions.SplitProjectViewAutoscrollFromSource
import com.intellij.platform.projectView.frontend.impl.TreeBasedFrontendProjectViewPane
import com.intellij.platform.projectView.frontend.pane.FrontendProjectViewPane
import com.intellij.platform.projectView.frontend.pane.FrontendProjectViewPaneAggregator
import com.intellij.platform.projectView.frontend.pane.id
import com.intellij.platform.projectView.pane.ProjectViewNodePath
import com.intellij.platform.projectView.pane.ProjectViewPaneDescriptorImpl
import com.intellij.platform.projectView.pane.ProjectViewPaneId
import com.intellij.platform.projectView.pane.projectViewPaneId
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.jdom.Element
import java.util.concurrent.CancellationException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.seconds

@State(name = "FrontendProjectView", storages = [Storage(StoragePathMacros.PRODUCT_WORKSPACE_FILE)])
internal class ProjectViewToolWindowServiceImpl(
  val project: Project,
) : ProjectViewToolWindowService, PersistentStateComponent<Element> {
  companion object {
    fun getInstance(project: Project): ProjectViewToolWindowServiceImpl =
      ProjectViewToolWindowService.getInstance(project) as ProjectViewToolWindowServiceImpl
  }
  private val menuActionGroup: DefaultActionGroup by lazy { DefaultActionGroup() }
  private val stateInitJob = CompletableDeferred<Unit>()
  private lateinit var defaultSelection: ProjectViewPaneId
  private val paneSelectJob = CompletableDeferred<Unit>()
  private val persistentState = ProjectViewToolWindowServiceState()
  private val currentPaneMutableFlow = MutableStateFlow<FrontendProjectViewPane?>(null)
  val currentPaneFlow: StateFlow<FrontendProjectViewPane?> = currentPaneMutableFlow.asStateFlow()
  val panes: Map<ProjectViewPaneId, FrontendProjectViewPane>
    field = ConcurrentHashMap<ProjectViewPaneId, FrontendProjectViewPane>()
  private val currentPaneListener: ContentManagerListener = object : ContentManagerListener {
    override fun selectionChanged(event: ContentManagerEvent) {
      if (event.operation == ContentManagerEvent.ContentOperation.add) {
        val newPane = event.content.getUserData(PANE_KEY)
        val oldPane = currentPaneMutableFlow.value
        currentPaneMutableFlow.value = newPane
        currentPaneChanged(oldPane, newPane)
      }
    }
  }
  private val optionService = ProjectViewActionSupportImpl(currentPaneMutableFlow)

  override val currentPaneDescriptor: ProjectViewPaneDescriptorImpl?
    get() = currentPaneMutableFlow.value?.descriptor as ProjectViewPaneDescriptorImpl?

  private val orderedDescriptors = MutableStateFlow<List<ProjectViewPaneDescriptorImpl>>(emptyList())
  
  private fun descriptorOrder(): Map<ProjectViewPaneDescriptorImpl, Int> {
    return orderedDescriptors.value.withIndex().associate { it.value to it.index }
  }

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
      val paneContentManager = withContext(Dispatchers.UI) {
        PaneContentManager(toolWindow)
      }
      launch(CoroutineName("Managing PV TW content")) {
        paneContentManager.manageContent()
      }
      launch(CoroutineName("Pane management")) {
        val managementScope = this
        val aggregator = FrontendProjectViewPaneAggregator.getInstance(project)
        val activeJobs = CopyOnWriteArraySet<ManagePaneJob>()
        aggregator.getPaneDescriptorsFlow().collect { paneDescriptors ->
          LOG.debug { "Pane descriptors updated: $paneDescriptors" }
          orderedDescriptors.value = paneDescriptors
          defaultSelection = paneDescriptors.firstOrNull { it.isDefault }?.id ?: defaultSelectedPaneId()
          val actualIds = paneDescriptors.map { it.id }.toSet()
          for ((existingManager, existingJob) in activeJobs) {
            val existingDescriptor = existingManager.descriptor
            if (existingDescriptor.id !in actualIds) {
              LOG.debug { "The pane ${existingDescriptor.id} is gone, cancelling its job" }
              existingJob.cancel(CancellationException("The descriptor is no longer present: $existingDescriptor"))
            }
          }
          for (descriptor in paneDescriptors) {
            val existingJob = activeJobs.find { it.descriptor == descriptor }
            if (existingJob != null) {
              LOG.debug { "The pane descriptor is already managed, skipping: $descriptor" }
              continue
            }
            // Now there are two possibilities: either it's a new pane (no such ID in activeJobs), or the descriptor has changed.
            // In the second case, it's important that we don't cancel the old job too early,
            // because it would cause content selection changes and therefore UI flickering.
            // The new content is created first, then it's replaced in a single EDT event,
            // and then the old content is disposed, which will cancel the old job automatically.
            LOG.debug { "Initializing the pane ${descriptor.id}, descriptor = $descriptor" }
            val pane = withContext(Dispatchers.UI) {
              aggregator.createPane(descriptor)
            }
            val paneManager = PaneManager(paneContentManager, pane)
            val job = managementScope.launch(CoroutineName("Manage PV pane ${descriptor.id}")) {
              try {
                paneManager.managePane()
              }
              catch (e: Throwable) {
                rethrowControlFlowException(e)
                LOG.error("Failed to initialize pane ${descriptor.id}", e)
              }
              finally {
                LOG.debug { "Done with the pane ${descriptor.id}, descriptor = $descriptor" }
              }
            }
            val newJob = ManagePaneJob(paneManager, job)
            activeJobs += newJob
            job.invokeOnCompletion {
              activeJobs -= newJob
            }
          }
          // A very important step: the managers are allowed to run concurrently within a given snapshot,
          // but when the descriptors are updated next time, we can't have races between new and old descriptors,
          // as then the winner can't be determined.
          // Example: a pane with some ID1 and descriptor1 is created and begins to initialize.
          // Then an updated descriptor2 with the same ID1 is received, and a new job is launched.
          // If the second job manages to create its content faster, then the previous job will dispose and replace it when it catches up.
          // So we'll end up with a job for a stale descriptor, which will then be immediately canceled.
          // Within a given snapshot, IDs are unique, so no such races are possible.
          // So we only need to finish initializing the previous batch before proceeding to the next one.
          for ((manager, _) in activeJobs) {
            manager.awaitInitialization()
          }
        }
      }
      launch(CoroutineName("Always select opened file")) {
        SplitProjectViewAutoscrollFromSource.getInstance(project).manage()
      }
    }
  }
  
  private data class ManagePaneJob(
    val manager: PaneManager,
    val job: Job,
  ) {
    val descriptor: ProjectViewPaneDescriptorImpl
      get() = manager.descriptor
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

  override suspend fun show(requestFocus: Boolean) {
    withContext(Dispatchers.UI) {
      val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.PROJECT_VIEW)
      if (toolWindow == null) {
        LOG.error("Can't show the Project View tool window because it doesn't exist")
        return@withContext
      }
      suspendCancellableCoroutine { continuation ->
        val onShown: Runnable = {
          continuation.resume(Unit)
        }
        if (requestFocus) {
          toolWindow.activate(onShown, true)
        }
        else {
          toolWindow.show(onShown)
        }
      }
    }
  }

  override suspend fun selectNode(nodePath: ProjectViewNodePath) {
    val currentPane = currentPaneMutableFlow.value
    val pane: FrontendProjectViewPane? = if (currentPane?.id == nodePath.paneId) {
      currentPane
    }
    else {
      LOG.debug { "To select $nodePath, need to change the pane from ${currentPane?.id} to ${nodePath.paneId}" }
      withContext(Dispatchers.UI) {
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.PROJECT_VIEW)
        if (toolWindow == null) {
          LOG.error("The Project View tool window is not found")
          return@withContext null
        }
        val contentManager = toolWindow.contentManager
        val content = contentManager.contents.firstOrNull { content ->
          content.getUserData(PANE_KEY)?.id == nodePath.paneId
        }
        if (content == null) {
          return@withContext null
        }
        contentManager.setSelectedContent(content)
        content.getUserData(PANE_KEY)
      }
    }
    if (pane == null) {
      LOG.error("The pane ${nodePath.paneId} is not found")
      return
    }
    if (pane !is TreeBasedFrontendProjectViewPane) {
      LOG.info("Impossible to select in $pane, because it's not a tree-based pane")
      return
    }
    LOG.debug { "Selecting $nodePath" }
    pane.selectNode(nodePath)
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

  private inner class PaneManager(
    private val paneContentManager: PaneContentManager,
    private val pane: FrontendProjectViewPane,
  ) {
    val descriptor: ProjectViewPaneDescriptorImpl
      get() = pane.descriptor as ProjectViewPaneDescriptorImpl
    
    private val initialized = CompletableDeferred<Unit>()
    
    suspend fun managePane() {
      coroutineScope {
        val mainScope = this
        coroutineContext.job.invokeOnCompletion { 
          initialized.complete(Unit) // in the case of a failure
        }
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
          val paneContent = addOrReplaceContent() ?: return@launch // failure to add means the job is canceled
          // This is just a nice way to cancel this whole thing from anywhere.
          // Currently used by addOrReplaceContent  to replace the previous pane with the same ID.
          Disposer.register(paneContent.content) {
            LOG.debug { "Canceling the pane ${pane.id} because the TW content is disposed, descriptor = ${pane.descriptor}" }
            mainScope.cancel("TW content disposed")
          }
          if (mustSelectThisPane) {
            paneSelectJob.complete(Unit)
          }
          LOG.debug { "The pane has been initialized, ID = ${pane.id}, descriptor = ${pane.descriptor}" }
          initialized.complete(Unit)
          try {
            awaitCancellation()
          }
          finally {
            paneContentManager.removeAndDisposeContent(paneContent)
          }
        }
        LOG.debug { "Obtaining the pane aggregator to manage the pane ${pane.id}" }
        val aggregator = FrontendProjectViewPaneAggregator.getInstance(project)
        launch(CoroutineName("Pane ${pane.id} state updates")) {
          currentPaneMutableFlow.collectLatest { currentPane ->
            if (currentPane == pane) {
              LOG.debug { "The pane ${pane.id} is selected, starting to collect its updates"}
              try {
                val paneStateFlow = aggregator.getPaneStateFlow(descriptor)
                if (paneStateFlow == null) {
                  LOG.debug { "The pane ${pane.id} has no state flow, nothing to collect"}
                }
                else if (pane !is TreeBasedFrontendProjectViewPane) {
                  LOG.warn("The pane ${pane.id} has a state flow, but it's not a TreeBasedFrontendProjectViewPane")
                }
                else {
                  paneStateFlow.collect { event ->
                    try {
                      LOG.trace { "Update pane state for ${pane.id}: $event" }
                      pane.applyStateChange(event)
                    }
                    catch (e: Exception) {
                      rethrowControlFlowException(e)
                      LOG.error(
                        "An error has occurred when updating the pane ${pane.id} state, the state might be inconsistent. " +
                        "The problematic event was $event",
                        e
                      )
                    }
                  }
                }
              }
              catch (e: Exception) {
                rethrowControlFlowException(e)
                LOG.error("An error has occurred when requesting the state flow of the pane ${pane.id} state. ", e)
              }
              finally {
                LOG.debug { "The pane ${pane.id} has finished collecting its updates"}
              }
            }
          }
        }
        launch(CoroutineName("Pane ${pane.id} requests to the backend")) {
          val inChannel = (pane as? TreeBasedFrontendProjectViewPane)?.requestChannel
          val outChannel = aggregator.getPaneRequestChannel(descriptor)
          if (inChannel == null && outChannel != null) {
            LOG.warn(
              "The pane ${pane.id} only partially implements requests to the backend: " +
              "the backend provides a channel, but the UI doesn't"
            )
          }
          if (inChannel != null && outChannel == null) {
            LOG.warn(
              "The pane ${pane.id} only partially implements requests to the backend: " +
              "the UI provides a channel, but the backend doesn't"
            )
          }
          if (inChannel != null && outChannel != null) {
            LOG.debug { "Sending pane requests for ${pane.id}" }
            try {
              for (request in inChannel) {
                try {
                  LOG.trace { "Sent request for pane ${pane.id}: $request" }
                  outChannel.send(request)
                }
                catch (e: Exception) {
                  rethrowControlFlowException(e)
                  LOG.error(
                    "An error has occurred when trying to send a request to the backend. " +
                    "The problematic request was $request",
                    e
                  )
                }
              }
            }
            finally {
              LOG.debug { "Finished sending pane requests for ${pane.id}" }
            }
          }
        }
        LOG.debug { "Managing pane ${pane.id}" }
        pane.manage()
      }
    }
    
    suspend fun awaitInitialization() {
      initialized.await()
    }

    @RequiresEdt
    private fun addOrReplaceContent(): PaneContent? {
      paneContentManager.ensureContentsOrdered()

      var wasSelected = false
      val oldPaneContent = paneContentManager.findContent(pane.id)
      if (oldPaneContent != null) {
        wasSelected = paneContentManager.isSelected(oldPaneContent)
        // This has to be done now so the content is removed and added in a single EDT event.
        // The old pane's job will be canceled when the content is disposed.
        LOG.info("Replacing the pane with ID = ${pane.id}: old descriptor = ${oldPaneContent.descriptor}, new descriptor = ${pane.descriptor}")
        paneContentManager.removeAndDisposeContent(oldPaneContent)
      }

      val newContent = paneContentManager.createAndAddContent(pane) ?: return null

      if (wasSelected) {
        LOG.debug { "The previous pane with ID = ${pane.id} was selected, therefore selecting the new one with the same ID" }
        paneContentManager.setSelectedContent(newContent)
      }
      return newContent
    }
  }
  
  private data class PaneContent(val pane: FrontendProjectViewPane, val content: Content) {
    val id: ProjectViewPaneId
      get() = pane.id
    
    val descriptor: ProjectViewPaneDescriptorImpl
      get() = pane.descriptor as ProjectViewPaneDescriptorImpl
  }
  
  private inner class PaneContentManager @RequiresEdt constructor(toolWindow: ToolWindow) {
    private val contentManager = toolWindow.contentManager
    
    suspend fun manageContent() {
      coroutineScope {
        launch(Dispatchers.UI + CoroutineName("Current pane listener")) {
          contentManager.addContentManagerListener(currentPaneListener)
          awaitCancellationAndInvoke {
            contentManager.removeContentManagerListener(currentPaneListener)
          }
        }
        launch(Dispatchers.UI + CoroutineName("Content ordering")) {
          orderedDescriptors.collectLatest {
            ensureContentsOrdered()
          }
        }
      }
    }

    @RequiresEdt
    fun findContent(id: ProjectViewPaneId): PaneContent? {
      for (i in 0 until contentManager.contentCount) {
        val content = contentManager.getContent(i) ?: continue
        val pane = content.getUserData(PANE_KEY) ?: continue
        if (pane.id == id) return PaneContent(pane, content)
      }
      return null
    }
    
    @RequiresEdt
    fun isSelected(paneContent: PaneContent): Boolean {
      return contentManager.isSelected(paneContent.content)
    }

    @RequiresEdt
    fun createAndAddContent(pane: FrontendProjectViewPane): PaneContent? {
      val newContent = ContentFactory.getInstance().createContent(
        /* component = */ pane.component,
        /* displayName = */ (pane.descriptor as ProjectViewPaneDescriptorImpl).presentableName,
        /* isLockable = */ false
      )
      newContent.putUserData(PANE_KEY, pane)
      val index = findSuitableIndex(pane) ?: return null
      contentManager.addContent(newContent, index)
      panes[pane.id] = pane
      LOG.info("Added pane ${pane.id}, descriptor ${pane.descriptor}")
      return PaneContent(pane, newContent)
    }

    private fun findSuitableIndex(pane: FrontendProjectViewPane): Int? {
      val order = descriptorOrder()
      // We update the order before launching the managing job.
      // So no order can only mean the order was updated again, the descriptor is no longer there, and our job is being canceled.
      // Returning null early here to avoid UI flickering because the content will be immediately removed.
      val ourOrder = order[pane.descriptor] ?: return null
      val bisectIndex = (0 until contentManager.contentCount).toList().binarySearch { i ->
        val existingContent = contentManager.getContent(i)
        // Any null here means the content doesn't belong to the ordered sequence for any reason.
        // The most likely scenario is that it's about to be removed (its job is being canceled).
        // Or it could be a content added externally.
        // In either case, we keep such contents last as we're not interested in them.
        val existingDescriptor = existingContent?.getUserData(PANE_KEY)?.descriptor ?: return@binarySearch 1
        val existingOrder = order[existingDescriptor] ?: return@binarySearch 1
        existingOrder.compareTo(ourOrder)
      }
      val index = if (bisectIndex >= 0) {
        LOG.warn("We have two panes with the same order (=$ourOrder), shouldn't be possible: " +
                 "$pane and ${contentManager.getContent(bisectIndex)?.getUserData(PANE_KEY)}")
        bisectIndex + 1 // add after the dup pane
      }
      else {
        -bisectIndex - 1
      }
      return index
    }

    @RequiresEdt
    fun setSelectedContent(newContent: PaneContent) {
      contentManager.setSelectedContent(newContent.content)
    }

    @RequiresEdt
    fun removeAndDisposeContent(paneContent: PaneContent) {
      if (contentManager.removeContent(paneContent.content, true)) {
        panes -= paneContent.id
        LOG.info("Removed pane ${paneContent.id}, descriptor ${paneContent.descriptor}")
      }
    }

    @RequiresEdt
    fun ensureContentsOrdered() {
      val order = descriptorOrder() // take a snapshot as it's updated concurrently
      val currentDescriptorOrder = (0 until contentManager.contentCount).mapNotNull { i ->
        // A null safety chain because we're only interested in descriptors that are still valid and present.
        // Other stuff (contents not representing panes, descriptors that are about to be removed) is not relevant.
        contentManager.getContent(i)?.let { content ->
          content.getUserData(PANE_KEY)?.descriptor?.let { descriptor ->
            order[descriptor]
          }
        }
      }
      if (currentDescriptorOrder.isSorted()) return
      val contentsBeforeSelected = mutableListOf<Content>()
      val contentsAfterSelected = mutableListOf<Content>()
      val selectedContent = contentManager.selectedContent
      var isAfterSelected = false
      for (content in contentManager.contents) {
        if (content == selectedContent) {
          isAfterSelected = true // don't remove the selected content itself
        }
        else if (isAfterSelected) {
          contentsAfterSelected += content
          contentManager.removeContent(content, false)
        }
        else {
          contentsBeforeSelected += content
          contentManager.removeContent(content, false)
        }
      }
      for ((index, content) in contentsBeforeSelected.withIndex()) {
        contentManager.addContent(content, index)
      }
      for (content in contentsAfterSelected) {
        contentManager.addContent(content, contentManager.contentCount)
      }
    }
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
