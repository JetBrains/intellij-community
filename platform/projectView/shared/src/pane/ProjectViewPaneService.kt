// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(FlowPreview::class)
package com.intellij.platform.projectView.pane

import com.intellij.codeWithMe.ClientId
import com.intellij.codeWithMe.asContextElement
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
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.annotations.ApiStatus
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.time.Duration.Companion.seconds

@ApiStatus.Internal
interface ProjectViewPaneProvider {
  suspend fun createPanes(project: Project): List<ProjectViewPaneModel>
}

@OptIn(ExperimentalAtomicApi::class)
@ApiStatus.Internal
open class ProjectViewPaneService(
  private val project: Project,
  coroutineScope: CoroutineScope,
  private val getProviders: () -> List<ProjectViewPaneProvider>,
  private val debugName: String = "ProjectViewPaneService",
) {

  private val managers = AtomicReference<Map<ProjectViewPaneId, ProjectViewPaneManager>?>(null)
  
  private val managersDeferred = coroutineScope.async(CoroutineName("$debugName: pane computation")) {
    val result = hashMapOf<ProjectViewPaneId, ProjectViewPaneManager>()
    for (provider in getProviders()) {
      for (pane in provider.createPanes(project)) {
        val manager = createProjectViewPaneManager(pane)
        result[manager.id] = manager
      }
    }
    managers.store(result)
    result
  }

  init {
    coroutineScope.launch(CoroutineName("$debugName: pane management")) {
      val panes = managersDeferred.await()
      supervisorScope {
        for (pane in panes.values) {
          launch(CoroutineName("$debugName: pane ${pane.id}")) {
            pane.manage()
          }
        }
      }
    }
  }

  suspend fun getPaneRequestChannel(paneId: ProjectViewPaneId): SendChannel<ProjectViewPaneRequest> {
    return managersDeferred.await()[paneId]?.getRequestChannel() ?: Channel<ProjectViewPaneRequest>(capacity = 0).also { it.close() }
  }

  suspend fun getPaneDescriptors(): List<ProjectViewPaneDescriptorImpl> {
    return managersDeferred.await().values.toList().map { it.descriptor }
  }

  suspend fun getPaneStateFlow(paneId: ProjectViewPaneId): Flow<ProjectViewPaneStateEvent> {
    return managersDeferred.await()[paneId]?.getPaneStateFlow() ?: emptyFlow()
  }
  
  fun getPane(paneId: ProjectViewPaneId): ProjectViewPaneModel? {
    return managers.load()?.get(paneId)?.pane
  }

  suspend fun findNodeForOpenedFile(paneId: ProjectViewPaneId, editorChoice: EditorChoice, isInvokedManually: Boolean): ProjectViewNodePath? {
    val managers = managers.load() ?: return null
    val pane = managers[paneId]?.pane ?: return null
    return pane.findNodeForSelectIn(SelectByEditorImpl(editorChoice, isInvokedManually))
  }

  suspend fun findNodeForSelectIn(selectInRequestDTO: SelectInRequestDTO): ProjectViewNodePath? {
    val managers = managers.load() ?: return null
    val manager = managers.values.firstOrNull { pane ->
      pane.descriptor.selectInTargetDescriptors.any { selectInTargetDescriptor ->
        selectInTargetDescriptor.id == selectInRequestDTO.targetId
      }
    } ?: return null
    val selectInRequest = selectInRequestDTO.toSelectInRequest(project) ?: return null
    return manager.pane.findNodeForSelectIn(selectInRequest)
  }
}

private suspend fun createProjectViewPaneManager(pane: ProjectViewPaneModel): ProjectViewPaneManager {
  val descriptor = pane.describe(ProjectViewPaneDescriptorBuilderImpl())
  return ProjectViewPaneManager(pane, descriptor as ProjectViewPaneDescriptorImpl)
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

  fun getPaneStateFlow(): Flow<ProjectViewPaneStateEvent> = flow {
    subscriberCount.update { it + 1 }
    try {
      stateBuilder.getStateFlow().collect(this)
    }
    finally {
      subscriberCount.update { it - 1 }
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

private val LOG = logger<ProjectViewPaneService>()
