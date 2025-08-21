// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.changes

import com.intellij.openapi.application.readAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.*
import com.intellij.platform.project.ProjectId
import com.intellij.platform.project.findProjectOrNull
import com.intellij.platform.vcs.changes.ChangeListManagerState
import com.intellij.platform.vcs.impl.shared.rpc.ChangeDto
import com.intellij.platform.vcs.impl.shared.rpc.ChangeListDto
import com.intellij.platform.vcs.impl.shared.rpc.ChangeListsApi
import com.intellij.platform.vcs.impl.shared.rpc.ContentRevisionDto
import com.intellij.util.messages.SimpleMessageBusConnection
import com.intellij.vcs.VcsDisposable
import com.intellij.vcs.toDto
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch

internal class ChangeListsApiImpl : ChangeListsApi {
  override suspend fun areChangeListsEnabled(projectId: ProjectId): Flow<Boolean> =
    projectScopedFlow(projectId, object : CallbackFlowHandler<Boolean> {
      override fun getInitialValue(project: Project): Boolean = ChangeListManager.getInstance(project).areChangeListsEnabled()

      override fun initMessageBusConnection(scope: ProducerScope<Boolean>, connection: SimpleMessageBusConnection, project: Project) {
        connection.subscribe(ChangeListAvailabilityListener.TOPIC, object : ChangeListAvailabilityListener {
          override fun onAfter() {
            scope.trySend(ChangeListManager.getInstance(project).areChangeListsEnabled())
          }
        })
      }
    })

  override suspend fun getChangeListManagerState(projectId: ProjectId): Flow<ChangeListManagerState> =
    projectScopedFlow(projectId, object : CallbackFlowHandler<ChangeListManagerState> {
      override fun getInitialValue(project: Project): ChangeListManagerState =
        ChangeListManager.getInstance(project).changeListManagerState

      override fun initMessageBusConnection(scope: ProducerScope<ChangeListManagerState>, connection: SimpleMessageBusConnection, project: Project) {
        connection.subscribe(ChangesListManagerStateListener.TOPIC, ChangesListManagerStateListener.adapter {
          scope.trySend(ChangeListManager.getInstance(project).changeListManagerState)
        })
      }
    })

  override suspend fun getChangeLists(projectId: ProjectId): Flow<List<ChangeListDto>> =
    projectScopedFlow(projectId, object : CallbackFlowHandler<List<ChangeListDto>> {
      override fun getInitialValue(project: Project): List<ChangeListDto> =
        ChangeListManager.getInstance(project).changeLists.map { changeList -> changeList.toDto() }

      override fun initMessageBusConnection(scope: ProducerScope<List<ChangeListDto>>, connection: SimpleMessageBusConnection, project: Project) {
        connection.subscribe(
          ChangeListListener.TOPIC,
          object : ChangeListAdapter() {
            override fun changeListsChanged() {
              scope.trySend(ChangeListManager.getInstance(project).changeLists.map { changeList -> changeList.toDto() })
            }
          }
        )
      }
    })

  private fun <T> projectScopedFlow(projectId: ProjectId, handler: CallbackFlowHandler<T>): Flow<T> {
    val project = projectId.findProjectOrNull() ?: return emptyFlow()

    return callbackFlow {
      val messageBusConnection = readAction {
        if (project.isDisposed) {
          close()
          return@readAction null
        }

        launch {
          send(handler.getInitialValue(project))
        }

        project.messageBus.connect().also { messageBusConnection: SimpleMessageBusConnection ->
          handler.initMessageBusConnection(this, messageBusConnection, project)
        }
      }

      awaitClose {
        messageBusConnection?.disconnect()
      }
    }.buffer(onBufferOverflow = BufferOverflow.DROP_OLDEST)
  }

  private interface CallbackFlowHandler<T> {
    fun getInitialValue(project: Project): T

    fun initMessageBusConnection(scope: ProducerScope<T>, connection: SimpleMessageBusConnection, project: Project)
  }

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
    filePath = file.toDto(),
    localValue = this,
  )
}