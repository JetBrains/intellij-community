// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.shared.changes

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.LocalChangeList
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserSpecificNode
import com.intellij.platform.project.ProjectId
import com.intellij.platform.project.projectId
import com.intellij.platform.project.projectIdOrNull
import com.intellij.platform.vcs.changes.ChangeListManagerState
import com.intellij.platform.vcs.impl.shared.RdLocalChanges
import com.intellij.platform.vcs.impl.shared.rpc.ChangeId
import com.intellij.platform.vcs.impl.shared.rpc.ChangeListsApi
import com.intellij.platform.vcs.impl.shared.rpc.FilePathDto
import com.intellij.platform.vcs.impl.shared.rpc.UnversionedFilesDto
import fleet.rpc.client.durable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
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
        observeUnversionedFiles(observeManyFilesLimit(), api, projectId),
        observeIgnoredFiles(ChangesViewSettings.getInstance(project), api, projectId)
      ) { changeListDtos, unversionedDtos, ignoredDtos ->
        val changeLists = changeListDtos.map { it.getChangeList(project) }
        ChangeLists(
          changeLists = changeLists,
          changesIdMapping = changeLists.flatMap { it.changes }.associateBy { ChangeId.getId(it) },
          unversionedFiles = (unversionedDtos as? UnversionedFilesDto.Files)?.files.orEmpty().map { it.filePath },
          ignoredFiles = ignoredDtos.map { it.filePath },
          unversionedFileCounts = (unversionedDtos as? UnversionedFilesDto.Counts)?.toFilePathCounts(),
        )
      }
    )
  }.stateIn(cs, SharingStarted.Eagerly, ChangeLists.EMPTY)

  private fun UnversionedFilesDto.Counts.toFilePathCounts() = FilePathCounts(fileCount, directoryCount)

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
    val unversionedFileCounts: FilePathCounts? = null,
  ) {
    companion object {
      val EMPTY = ChangeLists(emptyList(), emptyMap(), emptyList(), emptyList())
    }
  }

  data class FilePathCounts(val fileCount: Int, val directoryCount: Int)
}

/**
 * Restarts the subscription when [maxFilesInTree] changes, so the backend applies the current limit.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun observeUnversionedFiles(maxFilesInTree: Flow<Int>, api: ChangeListsApi, projectId: ProjectId): Flow<UnversionedFilesDto> =
  maxFilesInTree.flatMapLatest { limit -> api.getUnversionedFiles(projectId, limit) }

/**
 * Uses [ChangesBrowserSpecificNode.MANY_FILES_LIMIT_DEFAULT] when the registry value is not an integer.
 */
private fun observeManyFilesLimit(): Flow<Int> =
  Registry.get(ChangesBrowserSpecificNode.MANY_FILES_LIMIT_KEY).asStringFlow()
    .map { it.toIntOrNull() ?: ChangesBrowserSpecificNode.MANY_FILES_LIMIT_DEFAULT }
    .distinctUntilChanged()

@OptIn(ExperimentalCoroutinesApi::class)
internal fun observeIgnoredFiles(settings: ChangesViewSettings, api: ChangeListsApi, projectId: ProjectId): Flow<List<FilePathDto>> =
  settings.showIgnoredState.flatMapLatest { showIgnored ->
    if (showIgnored) api.getIgnoredFiles(projectId) else flowOf(emptyList())
  }
