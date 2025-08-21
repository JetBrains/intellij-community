// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.scopes

import com.intellij.ide.util.scopeChooser.ScopesFilterConditionType
import com.intellij.platform.project.ProjectId
import com.intellij.platform.rpc.RemoteApiProviderService
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import fleet.rpc.remoteApiDescriptor
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.Flow
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Rpc
interface ScopeModelRemoteApi : RemoteApi<Unit> {
  suspend fun createModelAndSubscribe(projectId: ProjectId, modelId: String, filterConditionType: ScopesFilterConditionType): Flow<SearchScopesInfo>?

  suspend fun openEditScopesDialog(projectId: ProjectId, selectedScopeId: String?): Deferred<String?>

  suspend fun performScopeSelection(scopeId: String, modelId: String, projectId: ProjectId): Deferred<Unit>

  companion object {
    @JvmStatic
    suspend fun getInstance(): ScopeModelRemoteApi {
      return RemoteApiProviderService.resolve(remoteApiDescriptor<ScopeModelRemoteApi>())
    }
  }
}