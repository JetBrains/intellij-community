// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.shared.changes

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.LocalChangeList
import com.intellij.platform.project.ProjectId
import com.intellij.platform.project.projectId
import com.intellij.platform.project.projectIdOrNull
import com.intellij.platform.vcs.changes.ChangeListManagerState
import com.intellij.platform.vcs.impl.shared.RdLocalChanges
import com.intellij.platform.vcs.impl.shared.rpc.ChangeId
import com.intellij.platform.vcs.impl.shared.rpc.ChangeListsApi
import com.intellij.platform.vcs.impl.shared.rpc.FilePathDto
import fleet.rpc.client.durable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class ChangeListsViewModel(
  private val project: Project,
  private val cs: CoroutineScope,
) : ChangeListDnDSupport {
  val areChangeListsEnabled: StateFlow<Boolean> = changeListsApiFlow(checkRegistry = false) { api, projectId ->
    emitAll(api.areChangeListsEnabled(projectId))
  }.stateIn(cs, SharingStarted.Eagerly, true)

  val changeListManagerState: StateFlow<ChangeListManagerState> = changeListsApiFlow(checkRegistry = false) { api, projectId ->
    emitAll(api.getChangeListManagerState(projectId))
  }.stateIn(cs, SharingStarted.Eagerly,
            ChangeListManagerState.Updating(ChangeListManagerState.FileHoldersState(true, true)))

  val changeListsState: StateFlow<ChangeLists> = changeListsApiFlow { api, projectId ->
    emitAll(
      combine(
        api.getChangeLists(projectId),
        api.getUnversionedFiles(projectId),
        api.getIgnoredFiles(projectId)
      ) { changeListDtos, unversionedDtos, ignoredDtos ->
        val changeLists = changeListDtos.map { it.getChangeList(project) }
        ChangeLists(
          changeLists = changeLists,
          changesIdMapping = changeLists.flatMap { it.changes }.associateBy { ChangeId.getId(it) },
          unversionedFiles = unversionedDtos.map { it.filePath },
          ignoredFiles = ignoredDtos.map { it.filePath }
        )
      }
    )
  }.stateIn(cs, SharingStarted.Eagerly, ChangeLists.EMPTY)

  fun resolveChange(changeId: ChangeId): Change? = changeListsState.value.changesIdMapping[changeId]

  override fun moveChangesTo(list: LocalChangeList, changes: List<Change>) {
    cs.launch {
      ChangeListsApi.getInstance().moveChanges(project.projectId(), changes.map(ChangeId::getId), list.id)
    }
  }

  override fun addUnversionedFiles(list: LocalChangeList, unversionedFiles: List<FilePath>) {
    cs.launch {
      ChangeListsApi.getInstance().addUnversionedFiles(project.projectId(), unversionedFiles.map(FilePathDto::toDto), list.id)
    }
  }

  private fun <T> changeListsApiFlow(checkRegistry: Boolean = true, flowProducer: suspend FlowCollector<T>.(ChangeListsApi, ProjectId) -> Unit): Flow<T> =
    if (checkRegistry && !RdLocalChanges.isEnabled()) emptyFlow()
    else flow {
      val projectId = project.projectIdOrNull() ?: return@flow
      val api = ChangeListsApi.getInstance()
      durable {
        flowProducer(api, projectId)
      }
    }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): ChangeListsViewModel = project.service()
  }

  class ChangeLists(
    val changeLists: List<LocalChangeList>,
    val changesIdMapping: Map<ChangeId, Change>,
    val unversionedFiles: List<FilePath>,
    val ignoredFiles: List<FilePath>,
  ) {
    companion object {
      val EMPTY = ChangeLists(emptyList(), emptyMap(), emptyList(), emptyList())
    }
  }
}