// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.changes

import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.ChangeListAdapter
import com.intellij.openapi.vcs.changes.ChangeListAvailabilityListener
import com.intellij.openapi.vcs.changes.ChangeListListener
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.changes.ChangeListManagerEx
import com.intellij.openapi.vcs.changes.ChangesListManagerStateProvider
import com.intellij.openapi.vcs.changes.LocalChangeList
import com.intellij.openapi.vcs.changes.LocalChangeListImpl
import com.intellij.openapi.vcs.changes.actions.ScheduleForAdditionAction
import com.intellij.platform.project.ProjectId
import com.intellij.platform.vcs.changes.ChangeListManagerState
import com.intellij.platform.vcs.impl.shared.rpc.ChangeDto
import com.intellij.platform.vcs.impl.shared.rpc.ChangeId
import com.intellij.platform.vcs.impl.shared.rpc.ChangeListDto
import com.intellij.platform.vcs.impl.shared.rpc.ChangeListsApi
import com.intellij.platform.vcs.impl.shared.rpc.FilePathDto
import com.intellij.util.asDisposable
import com.intellij.vcs.rpc.ProjectScopeRpcHelper.getProjectScoped
import com.intellij.vcs.rpc.ProjectScopeRpcHelper.projectScoped
import com.intellij.vcs.rpc.ProjectScopeRpcHelper.projectScopedCallbackFlow
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

internal class ChangeListsApiImpl : ChangeListsApi {
  override suspend fun areChangeListsEnabled(projectId: ProjectId): Flow<Boolean> =
    projectScopedCallbackFlow(projectId) { project, messageBusConnection ->
      messageBusConnection.subscribe(ChangeListAvailabilityListener.TOPIC, object : ChangeListAvailabilityListener {
        override fun onAfter(newState: Boolean) {
          trySend(newState)
        }
      })

      send(ChangeListManager.getInstance(project).areChangeListsEnabled())
    }.buffer(onBufferOverflow = BufferOverflow.DROP_OLDEST)

  override suspend fun getChangeListManagerState(projectId: ProjectId): Flow<ChangeListManagerState> =
    getProjectScoped(projectId) { project -> ChangesListManagerStateProvider.getInstance(project).state }
    ?: emptyFlow()

  override suspend fun getChangeLists(projectId: ProjectId): Flow<List<ChangeListDto>> =
    projectScopedCallbackFlow(projectId) { project, messageBusConnection ->
      val changeListManager = ChangeListManager.getInstance(project)
      messageBusConnection.subscribe(ChangeListListener.TOPIC, object : ChangeListAdapter() {
        override fun changeListsChanged() {
          launch { send(changeListManager.changeLists.map { ChangeListEqualityWrapper(it) }) }
        }
      })
      send(changeListManager.changeLists.map { ChangeListEqualityWrapper(it) })
    }.distinctUntilChanged().map { wrappers -> wrappers.map { it.changeList.toDto() } }

  override suspend fun getUnversionedFiles(projectId: ProjectId): Flow<List<FilePathDto>> =
    observeUnchangedFiles(projectId) { it.unversionedFilesPaths }

  override suspend fun getIgnoredFiles(projectId: ProjectId): Flow<List<FilePathDto>> =
    observeUnchangedFiles(projectId) { it.ignoredFilePaths }

  override suspend fun moveChanges(projectId: ProjectId, changes: List<ChangeId>, changeListId: String) =
    projectScoped(projectId) { project ->
      val changeListManager = ChangeListManager.getInstance(project)
      val targetChangeList = changeListManager.getChangeList(changeListId) ?: return@projectScoped
      val resolvedChanges = ChangesViewChangeIdProvider.getInstance(project).getChangeListChanges(changes)
      changeListManager.moveChangesTo(targetChangeList, resolvedChanges)
    }

  override suspend fun addUnversionedFiles(projectId: ProjectId, files: List<FilePathDto>, changeListId: String) =
    projectScoped(projectId) { project ->
      val changeListManager = ChangeListManagerEx.getInstanceEx(project)
      val targetChangeList = changeListManager.getChangeList(changeListId)
      val virtualFiles = files.mapNotNull { it.filePath.virtualFile }
      ScheduleForAdditionAction.Manager.addUnversionedFilesToVcsInBackground(project, targetChangeList, virtualFiles)
    }

  private suspend fun observeUnchangedFiles(projectId: ProjectId, onUpdate: (ChangeListManager) -> List<FilePath>): Flow<List<FilePathDto>> =
    projectScopedCallbackFlow(projectId) { project, _ ->
      val changeListManager = ChangeListManager.getInstance(project)
      changeListManager.addChangeListListener(object : ChangeListAdapter() {
        override fun unchangedFileStatusChanged(upToDate: Boolean) {
          if (upToDate) {
            launch {
              send(onUpdate(changeListManager))
            }
          }
        }
      }, this@projectScopedCallbackFlow.asDisposable())
      send(onUpdate(changeListManager))
    }.distinctUntilChanged().map { filePaths -> filePaths.map { FilePathDto.toDto(it) } }

  private fun LocalChangeList.toDto(): ChangeListDto = ChangeListDto(
    name = name,
    comment = comment,
    changes = changes.map(ChangeDto::toDto),
    isDefault = isDefault,
    id = id,
    localValue = this,
  )
}

/**
 * [LocalChangeListImpl.equals] compares only names, but the new state should be sent only if
 * the actuals changes set was updated.
 */
private class ChangeListEqualityWrapper(val changeList: LocalChangeList) {
  override fun equals(other: Any?): Boolean {
    if (changeList != (other as? ChangeListEqualityWrapper)?.changeList) return false
    if (changeList.changes.size != (other.changeList.changes.size)) return false

    val otherChangesIterator = other.changeList.changes.iterator()
    val thisChangesIterator = changeList.changes.iterator()
    while (thisChangesIterator.hasNext()) {
      val thisChange = thisChangesIterator.next()
      val otherChange = otherChangesIterator.next()
      if (thisChange != otherChange || thisChange.beforeRevision != otherChange.beforeRevision) return false
    }
    return true
  }

  override fun hashCode(): Int {
    return changeList.hashCode()
  }
}