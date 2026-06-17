// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(FlowPreview::class)
package com.intellij.platform.projectView.backend.pane

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.project.Project
import com.intellij.platform.projectView.actions.EditorChoice
import com.intellij.platform.projectView.pane.ProjectViewNodePath
import com.intellij.platform.projectView.pane.ProjectViewPaneDescriptorBuilderImpl
import com.intellij.platform.projectView.pane.ProjectViewPaneDescriptorImpl
import com.intellij.platform.projectView.pane.ProjectViewPaneId
import com.intellij.platform.projectView.pane.ProjectViewPaneRequest
import com.intellij.platform.projectView.pane.ProjectViewPaneStateEvent
import com.intellij.platform.projectView.pane.SelectInRequestDTO
import com.intellij.platform.projectView.pane.toSelectInRequest
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
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalAtomicApi::class)
@Service(Service.Level.PROJECT)
internal class BackendProjectViewPaneService(
  private val project: Project,
  coroutineScope: CoroutineScope,
) {
  companion object {
    fun getInstance(project: Project): BackendProjectViewPaneService = project.service()
    suspend fun getInstanceSuspend(project: Project): BackendProjectViewPaneService = project.serviceAsync()
  }
  
  private val managers = AtomicReference<Map<ProjectViewPaneId, BackendProjectViewPaneManager>?>(null)
  
  private val managersDeferred = coroutineScope.async(CoroutineName("BackendProjectViewPaneService: pane computation")) {
    val result = hashMapOf<ProjectViewPaneId, BackendProjectViewPaneManager>()
    for (provider in BackendProjectViewPaneProviderEP.extensionList) {
      for (pane in provider.createPanes(project)) {
        val manager = createBackendProjectViewPaneManager(pane)
        result[manager.id] = manager
      }
    }
    managers.store(result)
    result
  }

  init {
    coroutineScope.launch(CoroutineName("BackendProjectViewPaneService: pane management")) {
      val panes = managersDeferred.await()
      supervisorScope {
        for (pane in panes.values) {
          launch(CoroutineName("BackendProjectViewPaneService: pane ${pane.id}")) {
            pane.manage()
          }
        }
      }
    }
  }

  suspend fun getPaneRequestChannel(paneId: ProjectViewPaneId): SendChannel<ProjectViewPaneRequest> {
    return managersDeferred.await()[paneId]?.pane?.getRequestChannel() ?: Channel<ProjectViewPaneRequest>(capacity = 0).also { it.close() }
  }

  suspend fun getPaneDescriptors(): List<ProjectViewPaneDescriptorImpl> {
    return managersDeferred.await().values.toList().map { it.descriptor }
  }

  suspend fun getPaneStateFlow(paneId: ProjectViewPaneId): Flow<ProjectViewPaneStateEvent> {
    return managersDeferred.await()[paneId]?.getPaneStateFlow() ?: emptyFlow()
  }
  
  fun getPane(paneId: ProjectViewPaneId): BackendProjectViewPane? {
    return managers.load()?.get(paneId)?.pane
  }

  suspend fun findNodeForOpenedFile(paneId: ProjectViewPaneId, editorChoice: EditorChoice): ProjectViewNodePath? {
    val managers = managers.load() ?: return null
    val pane = managers[paneId]?.pane ?: return null
    return pane.findNodeForEditor(editorChoice)
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

private suspend fun createBackendProjectViewPaneManager(pane: BackendProjectViewPane): BackendProjectViewPaneManager {
  val descriptor = pane.describe(ProjectViewPaneDescriptorBuilderImpl())
  return BackendProjectViewPaneManager(pane, descriptor as ProjectViewPaneDescriptorImpl)
}

private class BackendProjectViewPaneManager(val pane: BackendProjectViewPane, val descriptor: ProjectViewPaneDescriptorImpl) {
  val id: ProjectViewPaneId
    get() = descriptor.id

  private val subscriberCount = MutableStateFlow(0)
  private val stateBuilder = ProjectViewPaneStateBuilderImpl()

  suspend fun manage() {
    coroutineScope {
      subscriberCount.map { subscriberCount -> subscriberCount > 0 }
        .distinctUntilChanged() // filter out activate / deactivate events
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

  fun getPaneStateFlow(): Flow<ProjectViewPaneStateEvent> = flow {
    subscriberCount.update { it + 1 }
    try {
      stateBuilder.getStateFlow().collect(this)
    }
    finally {
      subscriberCount.update { it - 1 }
    }
  }
}
