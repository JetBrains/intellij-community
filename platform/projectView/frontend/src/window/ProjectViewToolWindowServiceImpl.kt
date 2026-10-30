@file:OptIn(ExperimentalAtomicApi::class, AwaitCancellationAndInvoke::class, FlowPreview::class)
// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.frontend.window

import com.intellij.configurationStore.SettingsSavingComponent
import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.ide.projectView.impl.ProjectViewInitNotifier
import com.intellij.ide.projectView.impl.ProjectViewPane
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.UI
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowContentUiType
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ProjectFrameCapabilitiesService
import com.intellij.openapi.wm.impl.content.ToolWindowContentUi
import com.intellij.platform.projectView.actions.ProjectViewActionSupport
import com.intellij.platform.projectView.frontend.actions.ProjectViewActionSupportImpl
import com.intellij.platform.projectView.frontend.actions.ProjectViewToolbarActionGroup
import com.intellij.platform.projectView.frontend.actions.SplitProjectViewAutoscrollFromSource
import com.intellij.platform.projectView.frontend.impl.CachedFrontendProjectViewPane
import com.intellij.platform.projectView.frontend.impl.FrontendProjectViewUpdateRequestsService
import com.intellij.platform.projectView.frontend.impl.TreeBasedFrontendProjectViewPane
import com.intellij.platform.projectView.frontend.pane.FrontendProjectViewPane
import com.intellij.platform.projectView.frontend.pane.FrontendProjectViewPaneAggregator
import com.intellij.platform.projectView.frontend.pane.RestorePaneStateOptions
import com.intellij.platform.projectView.frontend.pane.RestorePaneStateOptionsImpl
import com.intellij.platform.projectView.frontend.pane.id
import com.intellij.platform.projectView.pane.ProjectViewClearStateEvent
import com.intellij.platform.projectView.pane.ProjectViewNodePath
import com.intellij.platform.projectView.pane.ProjectViewPaneDescriptorImpl
import com.intellij.platform.projectView.pane.ProjectViewPaneId
import com.intellij.platform.projectView.pane.ProjectViewPaneKind
import com.intellij.platform.projectView.pane.projectViewPaneId
import com.intellij.platform.projectView.runSafelyCancellable
import com.intellij.platform.projectView.window.ProjectViewToolWindowService
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.content.ContentManagerEvent
import com.intellij.ui.content.ContentManagerListener
import com.intellij.util.AwaitCancellationAndInvoke
import com.intellij.util.awaitCancellationAndInvoke
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

@State(name = "FrontendProjectView", storages = [Storage(StoragePathMacros.PRODUCT_WORKSPACE_FILE)])
internal class ProjectViewToolWindowServiceImpl(
  val project: Project,
) : ProjectViewToolWindowService, PersistentStateComponent<Element>, SettingsSavingComponent {
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
  private val activePanes = ConcurrentHashMap.newKeySet<ProjectViewPaneId>()
  /**
   * Restores the state of a pane when it becomes selected and saves it when it becomes unselected.
   *
   * The content manager also unselects the selected content before it removes it, so a removed pane saves its state too.
   * The cached pane saves its state the same way, so the real pane with the same ID restores it.
   * Both happen in the same EDT event as the selection change, even if the tool window is hidden.
   */
  private val paneSelectionListener: ContentManagerListener = object : ContentManagerListener {
    override fun selectionChanged(event: ContentManagerEvent) {
      val pane = event.content.getUserData(PANE_KEY)
      when (event.operation) {
        ContentManagerEvent.ContentOperation.add -> {
          if (pane != null) {
            loadPaneState(pane)
          }
          val oldPane = currentPaneMutableFlow.value
          currentPaneMutableFlow.value = pane
          currentPaneChanged(oldPane, pane)
        }
        ContentManagerEvent.ContentOperation.remove -> {
          if (pane != null) {
            savePaneStateIfActive(pane)
          }
          val cachedPane = event.content.getUserData(CACHED_PANE_KEY)
          if (cachedPane != null) {
            saveCachedPaneState(cachedPane)
          }
        }
        ContentManagerEvent.ContentOperation.undefined, null -> { }
      }
    }
  }
  private val optionService = ProjectViewActionSupportImpl(currentPaneMutableFlow)

  override val currentPaneDescriptor: ProjectViewPaneDescriptorImpl?
    get() = currentPaneMutableFlow.value?.descriptorImpl

  private val orderedDescriptors = MutableStateFlow<List<ProjectViewPaneDescriptorImpl>>(emptyList())
  
  private fun descriptorOrder(): Map<ProjectViewPaneDescriptorImpl, Int> {
    return orderedDescriptors.value.withIndex().associate { it.value to it.index }
  }

  override fun getActionSupport(): ProjectViewActionSupport = optionService

  @RequiresEdt
  override fun setupToolWindow(toolWindow: ToolWindow) {
    ProjectViewInitNotifier.getInstance(project).initStarted()
    toolWindow.setDefaultContentUiType(ToolWindowContentUiType.COMBO)
    val action = ActionManager.getInstance().getAction("ProjectViewToolbar") as? ActionGroup?
    if (action != null) toolWindow.setTitleActions(listOf(ProjectViewToolbarActionGroup(action)))
    toolWindow.setAdditionalGearActions(menuActionGroup)
    toolWindow.component.putClientProperty(ToolWindowContentUi.HIDE_ID_LABEL, "true")
    try {
      addCachedPaneContent(toolWindow)
    }
    catch (e: Throwable) {
      rethrowControlFlowException(e)
      LOG.error("Failed to add the cached pane", e)
    }
  }

  /**
   * Shows the cached tree of the pane to select until the first real pane is added.
   *
   * The pane to select is the same as the one that [manageToolWindow] selects.
   */
  @RequiresEdt
  private fun addCachedPaneContent(toolWindow: ToolWindow) {
    if (!stateInitJob.isCompleted) {
      LOG.debug { "The saved state is not loaded yet, not showing the cached pane" }
      return
    }
    val paneId = getStartupPaneId(service<ProjectFrameCapabilitiesService>()) ?: persistentState.getSelectedPaneState() ?: return
    val paneElement = persistentState.getPaneState(paneId)?.element
    @Suppress("HardCodedStringLiteral") // the name is saved from the descriptor
    val presentableName: @NlsSafe String? = paneElement?.getAttributeValue(PRESENTABLE_NAME_ATTRIBUTE)
    if (paneElement == null || presentableName == null) {
      LOG.debug { "The pane $paneId has no saved state or name, not showing the cached pane" }
      return
    }
    val pane = CachedFrontendProjectViewPane(paneId, presentableName)
    if (!pane.restoreStateFrom(paneElement)) {
      LOG.debug { "The pane $paneId has no cached presentation, not showing the cached pane" }
      return
    }
    val content = ContentFactory.getInstance().createContent(pane.component, presentableName, false)
    content.setPreferredFocusedComponent { pane.componentToFocus }
    content.putUserData(CACHED_PANE_KEY, pane)
    toolWindow.contentManager.addContent(content)
    ProjectViewInitNotifier.getInstance(project).initCachedNodesLoaded()
    LOG.info("Added the cached pane $paneId")
  }

  private fun getStartupPaneId(capabilities: ProjectFrameCapabilitiesService): ProjectViewPaneId? {
    return capabilities.getUiPolicy(project)?.projectPaneToActivateId?.let { projectViewPaneId(it) }
  }

  override suspend fun manageToolWindow(toolWindow: ToolWindow) {
    supervisorScope {
      val paneContentManager = withContext(Dispatchers.UI) {
        PaneContentManager(toolWindow)
      }
      launch(CoroutineName("Managing PV TW content")) {
        paneContentManager.manageContent()
      }
      val aggregator = FrontendProjectViewPaneAggregator.getInstance(project)
      val startupPaneId = getStartupPaneId(serviceAsync<ProjectFrameCapabilitiesService>())
      // The frame UI policy wins over the saved selection, as in the monolith.
      // The saved selection is read once, after the state is loaded: the content manager selects the first added content,
      // and that selection overwrites the saved value (IJPL-256315).
      val initialSelection = lazy { startupPaneId ?: persistentState.getSelectedPaneState() }
      launch(CoroutineName("Pane management")) {
        val managementScope = this
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
            if (pane == null) {
              LOG.warn(
                "The pane ${descriptor.id} doesn't have UI. " +
                "Either it was already removed by the provider, or the provider is buggy. " +
                "Descriptor = $descriptor"
              )
              continue
            }
            val paneManager = PaneManager(paneContentManager, pane, initialSelection)
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
      launch(CoroutineName("File open/close updates to backend")) {
        project.messageBus.connect(this).subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, object : FileEditorManagerListener {
          // deep = true because files may have children too, e.g. with Show Members, and children inherit file colors
          override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
            requestUpdate(file)
          }

          override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
            requestUpdate(file)
          }

          private fun requestUpdate(file: VirtualFile) {
            FrontendProjectViewUpdateRequestsService.getInstance(project).requestUpdate(file, true)
          }
        })
        awaitCancellation() // keep the subscription alive
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
    updateMenuActions(newPane)
  }

  private fun updateMenuActions(newPane: FrontendProjectViewPane?) {
    menuActionGroup.removeAll()
    if (newPane?.descriptorImpl?.kind == ProjectViewPaneKind.UI_ONLY) return
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
        selectPane(nodePath.paneId)
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

  @RequiresEdt
  fun selectDefaultPane() {
    selectPane(defaultSelection)
  }

  @RequiresEdt
  private fun selectPane(paneId: ProjectViewPaneId): FrontendProjectViewPane? {
    val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.PROJECT_VIEW)
    if (toolWindow == null) {
      LOG.error("The Project View tool window is not found")
      return null
    }
    val contentManager = toolWindow.contentManager
    val content = contentManager.contents.firstOrNull { content ->
      content.getUserData(PANE_KEY)?.id == paneId
    }
    if (content == null) {
      return null
    }
    contentManager.setSelectedContent(content)
    return content.getUserData(PANE_KEY)
  }

  override suspend fun save() {
    // Not sure if this thing can be invoked under a modal state, but it better run fast regardless.
    withContext(Dispatchers.UI + ModalityState.any().asContextElement()) {
      // Only save the current pane, because other panes either:
      // 1. already saved their state when they were unselected the last time or,
      // 2. even worse, may have never been loaded, which means they have empty states,
      // and we don't want to overwrite whatever was loaded from the persistent storage.
      val currentPane = currentPaneFlow.value
      if (currentPane != null) {
        savePaneStateIfActive(currentPane)
      }
    }
  }

  override fun getState(): Element = Element("projectView").also { element ->
    persistentState.writeStateTo(element)
    LOG.debug { "Serialized the state to save" }
  }

  override fun noStateLoaded() {
    loadState(Element("projectView"))
  }

  override fun loadState(state: Element) {
    persistentState.readStateFrom(state)
    stateInitJob.complete(Unit)
    LOG.debug { "Restored the saved state" }
  }
  
  private fun loadPaneState(pane: FrontendProjectViewPane) {
    val paneState = persistentState.getPaneState(pane.id)
    val isLightToSmartMigration =
      paneState?.paneKind == ProjectViewPaneKind.LIGHT &&
      pane.descriptorImpl.kind == ProjectViewPaneKind.BACKEND
    val restoreSelection = isLightToSmartMigration || paneState?.restoreSelection == true
    pane.safeRestoreStateFrom(paneState?.element, RestorePaneStateOptionsImpl(restoreSelection = restoreSelection))
    LOG.debug { "Applied the loaded state for ${pane.id}" }
  }
  
  @RequiresEdt
  private fun savePaneStateIfActive(pane: FrontendProjectViewPane) {
    if (pane.id !in activePanes) {
      LOG.debug { "Not saving the state of ${pane.id} because it's not active" }
      return
    }
    val paneElement = createPaneElement(pane.id, pane.descriptorImpl.presentableName)
    if (pane.safeSaveStateTo(paneElement)) {
      persistentState.putPaneState(pane.id, PaneState(paneElement, pane.descriptorImpl.kind))
      LOG.debug { "Saved the last state for ${pane.id}" }
    }
    else {
      LOG.debug { "Not saved the effectively empty state for ${pane.id}" }
    }
  }

  @RequiresEdt
  private fun saveCachedPaneState(pane: CachedFrontendProjectViewPane) {
    val paneElement = createPaneElement(pane.id, pane.presentableName)
    pane.saveStateTo(paneElement)
    persistentState.putPaneState(pane.id, PaneState(paneElement, paneKind = null, restoreSelection = true))
    LOG.debug { "Saved the state of the cached pane ${pane.id}" }
  }

  private fun createPaneElement(paneId: ProjectViewPaneId, presentableName: @NlsSafe String): Element {
    val paneElement = Element("pane")
    paneElement.setAttribute("pane", paneId.idString)
    paneElement.setAttribute(PRESENTABLE_NAME_ATTRIBUTE, presentableName)
    return paneElement
  }

  private inner class PaneManager(
    private val paneContentManager: PaneContentManager,
    private val pane: FrontendProjectViewPane,
    private val initialSelection: Lazy<ProjectViewPaneId?>,
  ) {
    val descriptor: ProjectViewPaneDescriptorImpl
      get() = pane.descriptorImpl
    
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
          val selectedPaneId = initialSelection.value ?: defaultSelection
          val mustSelectThisPane = selectedPaneId == pane.id
          // The wait lets the selected pane be added first, so the content manager selects it.
          // The next snapshot of descriptors is processed only after this pane is initialized.
          // So a selected pane that is absent from the current snapshot cannot arrive during the wait, and the wait only delays the snapshot.
          val selectedPaneIsInSnapshot = orderedDescriptors.value.any { it.id == selectedPaneId }
          if (!mustSelectThisPane && selectedPaneIsInSnapshot) {
            withTimeoutOrNull(PANE_SELECTION_TIMEOUT) { // at this point the state is already received, and panes are added very quickly
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
          if (mustSelectThisPane && paneSelectJob.complete(Unit)) {
            // Another pane can be added and selected by the content manager before this one, so select this pane explicitly.
            // A pane that arrives late keeps the current selection: the user may already work with the shown pane.
            if (paneContentManager.canTakeOverSelection()) {
              paneContentManager.setSelectedContent(paneContent)
            }
            else {
              LOG.info("The pane ${pane.id} is the one to select, but it arrived later than $PANE_SELECTION_TIMEOUT after the first pane, keeping the current selection")
            }
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
          currentPaneMutableFlow
            .map { it == pane }
            .distinctUntilChanged()
            .debounce { isCurrent -> if (isCurrent) Duration.ZERO else 60.seconds }
            .distinctUntilChanged()
            .collectLatest { isCurrent ->
              if (isCurrent) {
                LOG.debug { "The pane ${pane.id} is selected, starting to collect its updates" }
                try {
                  val paneStateFlow = aggregator.getPaneStateFlow(descriptor)
                  if (paneStateFlow == null) {
                    LOG.debug { "The pane ${pane.id} has no state flow, nothing to collect" }
                  }
                  else if (pane !is TreeBasedFrontendProjectViewPane) {
                    LOG.warn("The pane ${pane.id} has a state flow, but it's not a TreeBasedFrontendProjectViewPane")
                  }
                  else {
                    paneStateFlow.collect { event ->
                      runSafelyCancellable(LOG, taskDescription = { "updating the pane ${pane.id} state, event $event" }) {
                        LOG.trace { "Update pane state for ${pane.id}: $event" }
                        if (event is ProjectViewClearStateEvent) {
                          withContext(Dispatchers.UI) {
                            savePaneStateIfActive(pane) // save before clearing
                          }
                          activePanes -= pane.id
                        }
                        else {
                          activePanes += pane.id
                        }
                        pane.applyStateChange(event)
                      }
                    }
                  }
                }
                catch (e: Exception) {
                  rethrowControlFlowException(e)
                  LOG.error("An error has occurred when requesting the state flow of the pane ${pane.id} state. ", e)
                }
                finally {
                  LOG.debug { "The pane ${pane.id} has finished collecting its updates" }
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
                runSafelyCancellable(LOG, taskDescription = { "sending a request to the backend: $request" }) {
                  LOG.trace { "Sent request for pane ${pane.id}: $request" }
                  outChannel.send(request)
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
      get() = pane.descriptorImpl
  }

  private inner class PaneContentManager @RequiresEdt constructor(toolWindow: ToolWindow) {
    private val contentManager = toolWindow.contentManager
    // Written only in createAndAddContent on the EDT, read from any thread.
    @Volatile private var firstContentAddedAt: TimeMark? = null

    init {
      // Added at once, so the listener can't miss the selection of the first pane.
      contentManager.addContentManagerListener(paneSelectionListener)
    }

    suspend fun manageContent() {
      coroutineScope {
        launch(Dispatchers.UI + CoroutineName("Pane selection listener")) {
          awaitCancellationAndInvoke {
            contentManager.removeContentManagerListener(paneSelectionListener)
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
        /* displayName = */ pane.descriptorImpl.presentableName,
        /* isLockable = */ false
      )
      newContent.setPreferredFocusedComponent { pane.componentToFocus }
      newContent.putUserData(PANE_KEY, pane)
      val index = findSuitableIndex(pane) ?: return null
      if (firstContentAddedAt == null) {
        // Before the first real pane, the tool window can have only the cached pane.
        LOG.debug { "Removing ${contentManager.contentCount} contents before adding the first real pane ${pane.id}" }
        contentManager.removeAllContents(true)
      }
      contentManager.addContent(newContent, index)
      if (firstContentAddedAt == null) {
        firstContentAddedAt = TimeSource.Monotonic.markNow()
      }
      panes[pane.id] = pane
      LOG.info("Added pane ${pane.id}, descriptor ${pane.descriptor}")
      return PaneContent(pane, newContent)
    }

    /**
     * Returns `true` while the pane to select may still take the selection over from an earlier added pane.
     * The window closes [PANE_SELECTION_TIMEOUT] after the first pane is shown.
     */
    fun canTakeOverSelection(): Boolean {
      val firstContentAddedAt = firstContentAddedAt ?: return true
      return firstContentAddedAt.elapsedNow() < PANE_SELECTION_TIMEOUT
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

  override suspend fun awaitInitialization() {
    currentPaneMutableFlow.first { it != null } as TreeBasedFrontendProjectViewPane
  }
}

private class ProjectViewToolWindowServiceState {
  private val selectedPaneState = AtomicReference<ProjectViewPaneId?>(null)
  private val paneState = ConcurrentHashMap<ProjectViewPaneId, PaneState>()

  fun getSelectedPaneState(): ProjectViewPaneId? {
    return selectedPaneState.load()
  }

  fun putSelectedPaneState(state: ProjectViewPaneId?) {
    selectedPaneState.store(state)
  }

  fun getPaneState(paneId: ProjectViewPaneId): PaneState? {
    return paneState[paneId]
  }

  fun putPaneState(paneId: ProjectViewPaneId, state: PaneState) {
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
      panesElement.addContent(paneState.element.clone())
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
      // The pane kind is only needed for light => smart switch.
      // Therefore, it is not persisted, as it's only used in-process.
      paneState[paneId] = PaneState(paneElement, paneKind = null)
    }
  }
}

private data class PaneState(
  val element: Element,
  val paneKind: ProjectViewPaneKind?,
  /**
   * `true` if the next restore must also restore the selection.
   *
   * The cached pane sets it, because its state comes from a temporary pane, as with the light to smart switch.
   * It is not persisted, as it's only used in-process.
   */
  val restoreSelection: Boolean = false,
)

private fun defaultSelectedPaneId(): ProjectViewPaneId =
  projectViewPaneId(ProjectViewPane.ID)

/**
 * The saved pane name, which the cached pane shows before the descriptors arrive.
 */
private const val PRESENTABLE_NAME_ATTRIBUTE = "presentableName"

private val FrontendProjectViewPane.descriptorImpl: ProjectViewPaneDescriptorImpl
  get() = descriptor as ProjectViewPaneDescriptorImpl

/**
 * Calls [FrontendProjectViewPane.restoreStateFrom] and logs an exception instead of throwing it.
 */
@RequiresEdt
private fun FrontendProjectViewPane.safeRestoreStateFrom(element: Element?, options: RestorePaneStateOptions) {
  try {
    restoreStateFrom(element, options)
  }
  catch (e: Throwable) {
    rethrowControlFlowException(e)
    LOG.error("Failed to restore the state of the pane $id", e)
  }
}

/**
 * Calls [FrontendProjectViewPane.saveStateTo] and logs an exception instead of throwing it.
 *
 * @return `false` if [FrontendProjectViewPane.saveStateTo] throws, so the partly written state is not persisted
 */
@RequiresEdt
private fun FrontendProjectViewPane.safeSaveStateTo(element: Element): Boolean {
  return try {
    saveStateTo(element)
  }
  catch (e: Throwable) {
    rethrowControlFlowException(e)
    LOG.error("Failed to save the state of the pane $id", e)
    false
  }
}

/**
 * How long the tool window waits for the pane to select.
 * A pane that is not the one to select waits this long before it is added.
 * The pane to select takes the selection over only if it is added within this time after the first pane is shown.
 * The two periods do not overlap: the wait ends when a pane is shown, and the take-over window starts at that moment.
 */
private val PANE_SELECTION_TIMEOUT = 5.seconds

private val PANE_KEY = Key.create<FrontendProjectViewPane>("FrontendProjectViewPane")
private val CACHED_PANE_KEY = Key.create<CachedFrontendProjectViewPane>("CachedFrontendProjectViewPane")
private val LOG = logger<ProjectViewToolWindowServiceImpl>()
