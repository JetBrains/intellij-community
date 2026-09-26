// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.impl.projectlevelman

import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsMappingListener
import com.intellij.platform.project.ProjectId
import com.intellij.platform.vcs.impl.shared.rpc.FilePathDto
import com.intellij.platform.vcs.impl.shared.rpc.VcsMappingDto
import com.intellij.platform.vcs.impl.shared.rpc.VcsMappingsApi
import com.intellij.platform.vcs.impl.shared.rpc.VcsMappingsDto
import com.intellij.vcs.rpc.ProjectScopeRpcHelper.projectScopedCallbackFlow
import com.intellij.vcsUtil.VcsUtil
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer

internal class VcsMappingsApiImpl : VcsMappingsApi {
  override suspend fun getMappings(projectId: ProjectId): Flow<VcsMappingsDto> =
    projectScopedCallbackFlow(projectId) { project, messageBusConnection ->
      val vcsManager = ProjectLevelVcsManager.getInstance(project)
      messageBusConnection.subscribe(ProjectLevelVcsManager.VCS_CONFIGURATION_CHANGED, VcsMappingListener { trySend(getMappingsDto(vcsManager)) })

      if (vcsManager.areVcsesActivated()) {
        send(getMappingsDto(vcsManager))
      }
    }.buffer(onBufferOverflow = BufferOverflow.DROP_OLDEST)

  private fun getMappingsDto(vcsManager: ProjectLevelVcsManager): VcsMappingsDto {
    val mappings = vcsManager.getAllVcsRoots().map {
      VcsMappingDto(
        root = FilePathDto.toDto(VcsUtil.getFilePath(it.path)),
        vcs = it.vcs?.keyInstanceMethod,
      )
    }
    return VcsMappingsDto(mappings)
  }
}