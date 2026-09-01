// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(FlowPreview::class)
package com.intellij.platform.projectView.pane

import com.intellij.codeWithMe.ClientId
import com.intellij.codeWithMe.asContextElement
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.rethrowControlFlowException
import com.intellij.openapi.project.Project
import com.intellij.platform.projectView.actions.EditorChoice
import com.intellij.platform.projectView.actions.fromDTO
import com.intellij.platform.projectView.settings.ProjectViewPaneFileNestingValueImpl
import com.intellij.platform.projectView.settings.toSettingValue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.CancellationException
import java.util.concurrent.CopyOnWriteArraySet
import kotlin.time.Duration.Companion.seconds

@ApiStatus.Internal
interface ProjectViewPaneProvider {
  /**
   * Returns the panes this provider contributes, as a flow, because the set of panes may change at runtime
   * (the Scope panes, for example, come and go as the user edits the scope list).
   *
   * A provider is expected to keep emitting the same [ProjectViewPaneModel] instances for panes that haven't
   * changed: [ProjectViewPaneServiceBase] uses instance identity to tell an unchanged pane from a new one, and only
   * the new ones are started (and only the vanished ones are stopped). Consequently, a pane whose descriptor
   * changes must be emitted as a *new* instance, or the change won't be noticed.
   */
  fun createPanes(project: Project): Flow<List<ProjectViewPaneModel>>
}

@ApiStatus.Internal
interface ProjectViewPaneService {
  suspend fun getPaneDescriptorsFlow(): Flow<Collection<ProjectViewPaneDescriptorImpl>>

  suspend fun getPaneStateFlow(paneId: ProjectViewPaneId): Flow<ProjectViewPaneStateEvent>?

  suspend fun getPaneRequestChannel(paneId: ProjectViewPaneId): SendChannel<ProjectViewPaneRequest>?

  suspend fun findNodeForOpenedFile(paneId: ProjectViewPaneId, editorChoice: EditorChoice, isInvokedManually: Boolean): ProjectViewNodePath?

  suspend fun findNodeForSelectIn(selectInRequestDTO: SelectInRequestDTO): ProjectViewNodePath?
}

@ApiStatus.Internal
abstract class ProjectViewPaneServiceBase(
  private val project: Project,
  coroutineScope: CoroutineScope,
  private val getProviders: () -> List<ProjectViewPaneProvider>,
  private val debugName: String = "ProjectViewPaneService",
) : ProjectViewPaneService {

  protected abstract val isFrontend: Boolean

  /** `null` until the first set of panes has been computed, to tell "not ready yet" from "no panes at all". */
  private val managers = MutableStateFlow<Map<ProjectViewPaneId, ProjectViewPaneManager>?>(null)

  private suspend fun describe(pane: ProjectViewPaneModel): ProjectViewPaneDescriptorImpl {
    val builder = ProjectViewPaneDescriptorBuilderImpl()
    builder.kind = if (isFrontend) ProjectViewPaneKind.LIGHT else ProjectViewPaneKind.BACKEND
    val descriptor = pane.describe(builder)
    return descriptor as ProjectViewPaneDescriptorImpl
  }

  init {
    coroutineScope.launch(CoroutineName("$debugName: pane management")) {
      supervisorScope {
        managePanes(this)
      }
    }
  }

  private suspend fun managePanes(managementScope: CoroutineScope) {
    val paneFlows = getProviders().map { provider -> provider.createPanes(project) }
    if (paneFlows.isEmpty()) {
      managers.value = emptyMap() // combine() of nothing never emits, and everyone else waits for the first value
      return
    }
    val jobs = CopyOnWriteArraySet<ManagerJob>()
    combine(paneFlows) { panesByProvider -> panesByProvider.flatMap { it } }.collect { panes ->
      // The order is important, as within a given provider panes can be pre-ordered with the equal order() (for example, scope panes).
      val newPanesByDescriptor = panes.associateBy { describe(it) }
      val currentManagersById = mutableMapOf<ProjectViewPaneId, ProjectViewPaneManager>()
      for ((manager, job) in jobs) {
        if (manager.descriptor !in newPanesByDescriptor) {
          LOG.debug { "The descriptor is gone, cancelling the pane job: ${manager.descriptor}" }
          job.cancel(CancellationException("The descriptor is no longer present"))
        }
      }
      val dedupIds = hashSetOf<ProjectViewPaneId>()
      for ((descriptor, pane) in newPanesByDescriptor) {
        val id = descriptor.id
        if (id in dedupIds) {
          LOG.error("Duplicate Project View pane ID $id, only the first pane with this ID will be used")
          continue
        }
        dedupIds += id
        val existingManagerJob = jobs.find { it.manager.descriptor == descriptor }
        if (existingManagerJob != null) {
          LOG.debug { "Not launching a new job for the descriptor, because there's already a job: $descriptor" }
          currentManagersById[id] = existingManagerJob.manager
          continue
        }
        val manager = ProjectViewPaneManager(pane, descriptor)
        currentManagersById[manager.id] = manager
        val job = managementScope.launch(CoroutineName("$debugName: pane $id")) {
          manager.manage()
        }
        val managerJob = ManagerJob(manager, job)
        jobs += managerJob
        job.invokeOnCompletion { 
          jobs -= managerJob
        }
      }
      managers.value = currentManagersById.toMap()
    }
  }
  
  private data class ManagerJob(
    val manager: ProjectViewPaneManager,
    val job: Job,
  )

  /**
   * Waits until the first set of panes has been computed, then returns the manager of the given pane
   * from the *current* set, so that panes added after the initial computation are found too.
   */
  private suspend fun awaitManager(paneId: ProjectViewPaneId): ProjectViewPaneManager? {
    managers.first { it != null }
    return managers.value?.get(paneId)
  }

  override suspend fun getPaneRequestChannel(paneId: ProjectViewPaneId): SendChannel<ProjectViewPaneRequest> {
    return awaitManager(paneId)?.getRequestChannel() ?: Channel<ProjectViewPaneRequest>(capacity = 0).also { it.close() }
  }

  override suspend fun getPaneDescriptorsFlow(): Flow<Collection<ProjectViewPaneDescriptorImpl>> {
    return managers
      .filterNotNull()
      .map { managers -> managers.values.map { it.descriptor } }
      .distinctUntilChanged()
  }

  override suspend fun getPaneStateFlow(paneId: ProjectViewPaneId): Flow<ProjectViewPaneStateEvent> {
    return awaitManager(paneId)?.getPaneStateFlow() ?: emptyFlow()
  }

  fun getPane(paneId: ProjectViewPaneId): ProjectViewPaneModel? {
    return managers.value?.get(paneId)?.pane
  }

  override suspend fun findNodeForOpenedFile(paneId: ProjectViewPaneId, editorChoice: EditorChoice, isInvokedManually: Boolean): ProjectViewNodePath? {
    val managers = managers.value ?: return null
    val pane = managers[paneId]?.pane ?: return null
    // no need for withPaneActive because (Always) Select Opened File is only used for the active pane
    return pane.findNodeForSelectIn(SelectByEditorImpl(editorChoice, isInvokedManually))
  }

  override suspend fun findNodeForSelectIn(selectInRequestDTO: SelectInRequestDTO): ProjectViewNodePath? {
    val managers = managers.value ?: return null
    val manager = managers.values.firstOrNull { pane ->
      pane.descriptor.selectInTargetDescriptors.any { selectInTargetDescriptor ->
        selectInTargetDescriptor.id == selectInRequestDTO.targetId
      }
    } ?: return null
    val selectInRequest = selectInRequestDTO.toSelectInRequest(project) ?: return null
    return manager.withPaneActive { // need to wake it up and keep it alive for a while to search
      manager.pane.findNodeForSelectIn(selectInRequest)
    }
  }
}

private class ProjectViewPaneManager(val pane: ProjectViewPaneModel, val descriptor: ProjectViewPaneDescriptorImpl) {
  val id: ProjectViewPaneId
    get() = descriptor.id

  private val subscriberCount = MutableStateFlow(0)
  private val stateBuilder = ProjectViewPaneStateBuilderImpl(id)
  private val manageScopeDeferred = CompletableDeferred<CoroutineScope>()

  suspend fun manage() {
    coroutineScope {
      manageScopeDeferred.complete(this)
      launch(CoroutineName("State updates for PV pane $id")) {
        subscriberCount.map { subscriberCount -> subscriberCount > 0 }
          .distinctUntilChanged() // leave only activate / deactivate events
          .debounce { isActive -> if (isActive) 0.seconds else 30.seconds }
          .distinctUntilChanged() // filter out quick reconnects
          .collectLatest { isActive ->
            if (isActive) {
              try {
                pane.manageState(stateBuilder)
              }
              catch (e: Exception) {
                rethrowControlFlowException(e)
                LOG.error("The pane $id crashed", e)
              }
              finally {
                withContext(NonCancellable) {
                  stateBuilder.clear()
                }
              }
            }
          }
      }
    }
  }
  
  suspend inline fun <T> withPaneActive(code: () -> T): T {
    subscriberCount.update { it + 1 }
    return try {
      stateBuilder.awaitInitialized()
      code()
    }
    finally {
      subscriberCount.update { it - 1 }
    }
  }

  fun getPaneStateFlow(): Flow<ProjectViewPaneStateEvent> = flow {
    withPaneActive {
      stateBuilder.getStateFlow().collect(this)
    }
  }

  suspend fun getRequestChannel(): SendChannel<ProjectViewPaneRequest> {
    val scope = withTimeoutOrNull(15.seconds) {
      manageScopeDeferred.await()
    }
    if (scope == null) {
      throw IllegalStateException("The scope for pane $id doesn't exist. It's either already disposed or is stuck during the startup")
    }
    val requestChannel = Channel<ProjectViewPaneRequest>(capacity = Channel.BUFFERED)
    scope.launch(CoroutineName("Requests for PV pane $id") + ClientId.current.asContextElement()) {
      processRequests(requestChannel)
    }
    return requestChannel
  }

  private suspend fun processRequests(requestChannel: Channel<ProjectViewPaneRequest>) {
    for (request in requestChannel) {
      try {
        when (request) {
          is ProjectViewPaneLoadChildrenRequest -> pane.loadChildren(request.nodeId, ProjectViewPaneLoadChildrenOptionsImpl)
          is ProjectViewPaneSelectionChanged -> pane.setPaneSelected(request.paneId == id, ProjectViewPaneSelectionOptionsImpl)
          is ProjectViewPaneNavigateRequest -> pane.navigate(request.nodeId, ProjectViewPaneNavigateOptionsImpl(request.requestFocus))
          is ProjectViewPaneChangeOptionValueRequest -> pane.setOptionValue(request.option.fromDTO(), request.newValue)
          is ProjectViewPaneChangeSortKeyRequest -> pane.setSortKey(request.sortKey.toSettingValue())
          is ProjectViewPaneChangeFileNestingRequest -> pane.setFileNesting(
            ProjectViewPaneFileNestingValueImpl(
              request.isFileNestingOn,
              request.activeRules.map { it.toNestingRule() },
            )
          )
          is ProjectViewPaneCopyRequest -> pane.cutCopyPasteDeleteHandler.performCopy(request.nodeIds)
          is ProjectViewPaneCutRequest -> pane.cutCopyPasteDeleteHandler.performCut(request.nodeIds)
          is ProjectViewPanePasteRequest -> pane.cutCopyPasteDeleteHandler.performPaste(request.nodeIds)
          is ProjectViewPaneDeleteRequest -> pane.cutCopyPasteDeleteHandler.performDelete(request.nodeIds)
          is ProjectViewPaneDnDRequest -> pane.dndHandler.performInternalDnD(request.sourceIDs, request.targetID, ProjectViewDnDOptionsImpl(request.action))
        }
      }
      catch (e: Exception) {
        rethrowControlFlowException(e)
        LOG.error(
          "An error has occurred when processing a request for pane $id. " +
          "The problematic request was $request",
          e
        )
      }
    }
  }
}

private val LOG = logger<ProjectViewPaneServiceBase>()
