// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.problemsView.shared

import com.intellij.analysis.problemsView.toolWindow.splitApi.ProblemEventDto
import com.intellij.ide.vfs.VirtualFileId
import com.intellij.platform.project.ProjectId
import com.intellij.platform.rpc.RemoteApiProviderService
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import fleet.rpc.remoteApiDescriptor
import kotlinx.coroutines.flow.Flow
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Rpc
interface ProblemsViewApi : RemoteApi<Unit> {

  suspend fun getFileProblemsFlow(projectId: ProjectId, fileId: VirtualFileId) : Flow<List<ProblemEventDto>>

  suspend fun executeQuickFix(projectId: ProjectId, fileId: VirtualFileId, problemId: String, intentionId: String)

  suspend fun getProjectErrorsFlow(projectId: ProjectId): Flow<List<ProblemEventDto>>

  suspend fun changeProblemsViewImplementationForNextIdeRunAndRestart(shouldEnableSplitImplementation: Boolean)

  companion object {
    suspend fun getInstance(): ProblemsViewApi {
      return RemoteApiProviderService.resolve(remoteApiDescriptor<ProblemsViewApi>())
    }
  }
}
