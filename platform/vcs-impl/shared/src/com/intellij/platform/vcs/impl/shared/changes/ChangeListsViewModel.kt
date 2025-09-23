// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.shared.changes

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.LocalChangeList
import com.intellij.platform.project.ProjectId
import com.intellij.platform.project.projectIdOrNull
import com.intellij.platform.vcs.changes.ChangeListManagerState
import com.intellij.platform.vcs.impl.shared.RdLocalChanges
import com.intellij.platform.vcs.impl.shared.rpc.ChangeListsApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class ChangeListsViewModel(
  private val project: Project,
  private val cs: CoroutineScope,
) {
  val areChangeListsEnabled: StateFlow<Boolean> = changeListsApiFlow(checkRegistry = false) { api, projectId ->
    emitAll(api.areChangeListsEnabled(projectId))
  }.stateIn(cs, SharingStarted.Eagerly, false)

  val changeListManagerState: StateFlow<ChangeListManagerState> = changeListsApiFlow(checkRegistry = false) { api, projectId ->
    emitAll(api.getChangeListManagerState(projectId))
  }.stateIn(cs, SharingStarted.Eagerly,
            ChangeListManagerState.Updating(ChangeListManagerState.FileHoldersState(true, true)))

  val changeLists: StateFlow<ChangeLists> = changeListsApiFlow { api, projectId ->
    emitAll(api.getChangeLists(projectId).map { changeLists ->
      ChangeLists(changeLists.map { it.getChangeList(project) })
    })
  }.stateIn(cs, SharingStarted.Eagerly, ChangeLists(emptyList()))

  private fun <T> changeListsApiFlow(checkRegistry: Boolean = true, flowProducer: suspend FlowCollector<T>.(ChangeListsApi, ProjectId) -> Unit): Flow<T> =
    if (checkRegistry && !RdLocalChanges.isEnabled()) emptyFlow()
    else flow {
      val projectId = project.projectIdOrNull() ?: return@flow
      val api = ChangeListsApi.getInstance()
      flowProducer(api, projectId)
    }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): ChangeListsViewModel = project.service()
  }

  class ChangeLists(val lists: List<LocalChangeList>)
}