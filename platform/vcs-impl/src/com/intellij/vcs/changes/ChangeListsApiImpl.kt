// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.changes

import com.intellij.openapi.vcs.changes.*
import com.intellij.platform.project.ProjectId
import com.intellij.platform.vcs.changes.ChangeListManagerState
import com.intellij.platform.vcs.impl.shared.rpc.*
import com.intellij.vcs.rpc.ProjectScopeRpcHelper.getProjectScoped
import com.intellij.vcs.rpc.ProjectScopeRpcHelper.projectScopedCallbackFlow
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.emptyFlow

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
          trySend(changeListManager.changeLists.map { changeList -> changeList.toDto() })
        }
      })
      send(changeListManager.changeLists.map { changeList -> changeList.toDto() })
    }.buffer(onBufferOverflow = BufferOverflow.DROP_OLDEST)

  private fun LocalChangeList.toDto(): ChangeListDto = ChangeListDto(
    name = name,
    comment = comment,
    changes = changes.map { change -> change.toDto() },
    isDefault = isDefault,
    id = id,
    localValue = this,
  )

  private fun Change.toDto(): ChangeDto = ChangeDto(
    beforeRevision = beforeRevision?.toDto(),
    afterRevision = afterRevision?.toDto(),
    fileStatusId = fileStatus.id,
    localValue = this,
  )

  private fun ContentRevision.toDto() = ContentRevisionDto(
    revisionString = revisionNumber.asString(),
    filePath = FilePathDto.toDto(file),
    localValue = this,
  )
}