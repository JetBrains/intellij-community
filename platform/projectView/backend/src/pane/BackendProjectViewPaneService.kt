// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.platform.projectView.pane.SelectInRequest
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

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
    return managersDeferred.await()[paneId]?.pane?.getPaneStateFlow() ?: emptyFlow()
  }
  
  fun getPane(paneId: ProjectViewPaneId): BackendProjectViewPane? {
    return managers.load()?.get(paneId)?.pane
  }

  suspend fun findNodeForOpenedFile(paneId: ProjectViewPaneId, editorChoice: EditorChoice): ProjectViewNodePath? {
    val managers = managers.load() ?: return null
    val pane = managers[paneId]?.pane ?: return null
    return pane.findNodeForEditor(editorChoice)
  }

  suspend fun findNodeForSelectIn(selectInRequest: SelectInRequest): ProjectViewNodePath? {
    val managers = managers.load() ?: return null
    val manager = managers.values.firstOrNull { pane ->
      pane.descriptor.selectInTargetDescriptors.any { selectInTargetDescriptor ->
        selectInTargetDescriptor.id == selectInRequest.targetId
      }
    } ?: return null
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

  suspend fun manage() {
    pane.manage()
  }
}
