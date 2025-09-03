// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.impl.projectlevelman

import com.intellij.openapi.application.readAction
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsMappingListener
import com.intellij.platform.project.ProjectId
import com.intellij.platform.project.findProjectOrNull
import com.intellij.platform.vcs.impl.shared.rpc.VcsMappingDto
import com.intellij.platform.vcs.impl.shared.rpc.VcsMappingsApi
import com.intellij.platform.vcs.impl.shared.rpc.VcsMappingsDto
import com.intellij.vcs.toDto
import com.intellij.vcsUtil.VcsUtil
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch

internal class VcsMappingsApiImpl : VcsMappingsApi {
  override suspend fun getMappings(projectId: ProjectId): Flow<VcsMappingsDto> {
    val project = projectId.findProjectOrNull() ?: return emptyFlow()

    return callbackFlow {
      val messageBusConnection = readAction {
        if (project.isDisposed) {
          close()
          return@readAction null
        }

        val vcsManager = ProjectLevelVcsManager.getInstance(project)
        val connection = project.messageBus.connect()
        connection.subscribe(ProjectLevelVcsManager.VCS_CONFIGURATION_CHANGED, VcsMappingListener { trySend(getMappingsDto(vcsManager)) })

        if (vcsManager.areVcsesActivated()) {
          launch {
            send(getMappingsDto(vcsManager))
          }
        }

        connection
      }

      awaitClose {
        messageBusConnection?.disconnect()
      }
    }.buffer(onBufferOverflow = BufferOverflow.DROP_OLDEST)
  }

  private fun getMappingsDto(vcsManager: ProjectLevelVcsManager): VcsMappingsDto {
    val mappings = vcsManager.getAllVcsRoots().map {
      VcsMappingDto(
        root = VcsUtil.getFilePath(it.path).toDto(),
        vcs = it.vcs?.keyInstanceMethod,
      )
    }
    return VcsMappingsDto(mappings)
  }
}