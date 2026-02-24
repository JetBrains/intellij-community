// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.backend.pane

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.project.Project
import com.intellij.platform.projectView.pane.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
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
  
  private val panes = AtomicReference<Map<ProjectViewPaneProviderId, Map<ProjectViewPaneId, BackendProjectViewPane>>?>(null)
  
  private val panesDeferred = coroutineScope.async(CoroutineName("BackendProjectViewPaneService: pane computation")) {
    val result = hashMapOf<ProjectViewPaneProviderId, Map<ProjectViewPaneId, BackendProjectViewPane>>()
    for (provider in BackendProjectViewPaneProviderEP.extensionList) {
      val panes = hashMapOf<ProjectViewPaneId, BackendProjectViewPane>()
      for (pane in provider.createPanes(project)) {
        panes[pane.id] = pane
      }
      result[provider.id] = panes
    }
    panes.store(result)
    result
  }

  init {
    coroutineScope.launch(CoroutineName("BackendProjectViewPaneService: pane management")) {
      val panes = panesDeferred.await()
      supervisorScope {
        for (pane in panes.values.flatMap { it.values }) {
          launch(CoroutineName("BackendProjectViewPaneService: pane ${pane.id}")) {
            pane.manage()
          }
        }
      }
    }
  }

  suspend fun getPaneRequestChannel(providerId: ProjectViewPaneProviderId, paneId: ProjectViewPaneId): SendChannel<ProjectViewPaneRequest> {
    return panesDeferred.await()[providerId]?.get(paneId)?.getRequestChannel() ?: Channel<ProjectViewPaneRequest>(capacity = 0).also { it.close() }
  }

  suspend fun getPaneDescriptors(providerId: ProjectViewPaneProviderId): List<ProjectViewPaneDescriptor> {
    return panesDeferred.await()[providerId]?.values?.toList()?.map { it.descriptor } ?: emptyList()
  }

  suspend fun getPaneStateFlow(providerId: ProjectViewPaneProviderId, paneId: ProjectViewPaneId): Flow<ProjectViewPaneStateEvent> {
    return panesDeferred.await()[providerId]?.get(paneId)?.getPaneStateFlow() ?: emptyFlow()
  }
  
  fun getPane(providerId: ProjectViewPaneProviderId, paneId: ProjectViewPaneId): BackendProjectViewPane? {
    return panes.load()?.get(providerId)?.get(paneId)
  }
}
