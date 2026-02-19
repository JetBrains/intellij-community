// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.shared.rpc

import com.intellij.platform.project.ProjectId
import com.intellij.platform.rpc.RemoteApiProviderService
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import fleet.rpc.remoteApiDescriptor
import org.jetbrains.annotations.ApiStatus

/**
 * Used to notify backend about changes in [com.intellij.platform.vcs.impl.frontend.changes.ChangesViewDelegatingInclusionModel]/
 */
@Rpc
@ApiStatus.Internal
interface ChangesViewInclusionModelApi : RemoteApi<Unit> {
  suspend fun add(projectId: ProjectId, items: List<InclusionDto>)
  suspend fun remove(projectId: ProjectId, items: List<InclusionDto>)
  suspend fun set(projectId: ProjectId, items: List<InclusionDto>)
  suspend fun retain(projectId: ProjectId, items: List<InclusionDto>)
  suspend fun clear(projectId: ProjectId)

  /**
   * Notifies when the state of [BackendChangesViewEvent.InclusionChanged] applied on the frontend,
   * so the backend model can emit the corresponding event in
   * [com.intellij.vcs.changes.viewModel.BackendCommitChangesViewModel.inclusionChanged]
   */
  suspend fun notifyInclusionUpdateApplied(projectId: ProjectId)

  companion object {
    suspend fun getInstance(): ChangesViewInclusionModelApi =
      RemoteApiProviderService.resolve(remoteApiDescriptor<ChangesViewInclusionModelApi>())
  }
}
