// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.scopes

import com.intellij.ide.rpc.DataContextId
import com.intellij.ide.util.scopeChooser.ScopesFilterConditionType
import com.intellij.platform.ide.productMode.IdeProductMode
import com.intellij.platform.project.ProjectId
import com.intellij.platform.rpc.lite.LiteRemoteApiProviderService
import com.intellij.platform.runtime.product.ProductMode
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import fleet.rpc.remoteApiDescriptor
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.Flow
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Rpc
interface ScopeModelApi : RemoteApi<Unit> {
  suspend fun createModelAndSubscribe(
    projectId: ProjectId,
    modelId: String,
    filterConditionType: ScopesFilterConditionType,
    dataContextId: DataContextId?,
  ): Flow<SearchScopesInfo>?

  suspend fun openEditScopesDialog(projectId: ProjectId, selectedScopeId: String?, modelId: String): Deferred<String?>

  suspend fun performScopeSelection(scopeId: String, projectId: ProjectId): Deferred<Unit>

  companion object {
    // One instance, not one per call: the implementation carries cross-call state (deferred scope selection)
    private val localInstance by lazy { ScopeModelApiImpl() }

    @JvmStatic
    suspend fun getInstance(): ScopeModelApi {
      LiteRemoteApiProviderService.tryResolve(remoteApiDescriptor<ScopeModelApi>())?.let { return it }
      // Null is not "no backend": a connected frontend also resolves null until its protocol client
      // is up. Only a strictly-Light session uses the local implementation; everything else awaits
      // the connection (IJPL-252054).
      return if (IdeProductMode.getInstance().currentMode == ProductMode.LIGHT) localInstance
             else LiteRemoteApiProviderService.awaitConnectionAndResolve(remoteApiDescriptor<ScopeModelApi>())
    }
  }
}