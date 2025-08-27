// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.changes

import com.intellij.openapi.application.readAction
import com.intellij.openapi.vcs.changes.*
import com.intellij.platform.project.ProjectId
import com.intellij.platform.project.findProjectOrNull
import com.intellij.platform.vcs.impl.shared.rpc.ChangeDto
import com.intellij.platform.vcs.impl.shared.rpc.ChangeListDto
import com.intellij.platform.vcs.impl.shared.rpc.ChangeListsApi
import com.intellij.platform.vcs.impl.shared.rpc.ContentRevisionDto
import com.intellij.vcs.toDto
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch

internal class ChangeListsApiImpl : ChangeListsApi {
  override suspend fun areChangeListsEnabled(projectId: ProjectId): Flow<Boolean> {
    val project = projectId.findProjectOrNull() ?: return emptyFlow()

    return callbackFlow {
      val messageBusConnection = readAction {
        if (project.isDisposed) {
          close()
          return@readAction null
        }

        launch {
          send(ChangeListManager.getInstance(project).areChangeListsEnabled())
        }

        project.messageBus.connect().also {
          it.subscribe(ChangeListAvailabilityListener.TOPIC, object : ChangeListAvailabilityListener {
            override fun onAfter() {
              trySend(ChangeListManager.getInstance(project).areChangeListsEnabled())
            }
          })
        }
      }

      awaitClose {
        messageBusConnection?.disconnect()
      }
    }.buffer(onBufferOverflow = BufferOverflow.DROP_OLDEST)
  }

  override suspend fun getChangeLists(projectId: ProjectId): Flow<List<ChangeListDto>> {
    val project = projectId.findProjectOrNull() ?: return emptyFlow()

    return callbackFlow {
      val messageBusConnection = readAction {
        if (project.isDisposed) {
          close()
          return@readAction null
        }

        launch {
          send(ChangeListManager.getInstance(project).changeLists.map { changeList -> changeList.toDto() })
        }

        project.messageBus.connect().also {
          it.subscribe(
            ChangeListListener.TOPIC,
            object : ChangeListAdapter() {
              override fun changeListsChanged() {
                trySend(ChangeListManager.getInstance(project).changeLists.map { changeList -> changeList.toDto() })
              }
            }
          )
        }
      }

      awaitClose {
        messageBusConnection?.disconnect()
      }
    }.buffer(onBufferOverflow = BufferOverflow.DROP_OLDEST)
  }

  private fun ChangeList.toDto(): ChangeListDto = ChangeListDto(
    name = name,
    comment = comment,
    changes = changes.map { change -> change.toDto() },
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