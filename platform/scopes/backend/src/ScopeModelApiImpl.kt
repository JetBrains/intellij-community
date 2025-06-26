// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.scopes.backend

import com.intellij.ide.util.scopeChooser.*
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.packageDependencies.DependencyValidationManager
import com.intellij.platform.project.ProjectId
import com.intellij.platform.project.findProjectOrNull
import com.intellij.platform.rpc.backend.RemoteApiProvider
import com.intellij.platform.scopes.ScopeModelApi
import com.intellij.platform.scopes.SearchScopeData
import com.intellij.platform.scopes.SearchScopesInfo
import com.intellij.psi.search.scope.packageSet.NamedScopeManager
import fleet.rpc.remoteApiDescriptor
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import java.util.*

private val LOG = logger<ScopesModelApiImpl>()

internal class ScopesModelApiImpl : ScopeModelApi {
  private val modelIdToModel = mutableMapOf<String, AbstractScopeModel>()
  private val modelIdToScopes = mutableMapOf<String, ScopesState>()

  override suspend fun createModelAndSubscribe(projectId: ProjectId, modelId: String): Flow<SearchScopesInfo>? {
    val project = projectId.findProjectOrNull()
    if (project == null) {
      LOG.warn("Project not found for projectId: $projectId")
      return null
    }
    val model = project.getService(ScopeService::class.java)
      .createModel(EnumSet.of(
        ScopeOption.FROM_SELECTION,
        ScopeOption.USAGE_VIEW,
        ScopeOption.LIBRARIES,
        ScopeOption.SEARCH_RESULTS
      ))
    modelIdToModel[modelId] = model
    val flow = subscribeToModelUpdates(model, modelId, project)
    model.refreshScopes(null)

    NamedScopeManager.getInstance(project).addScopeListener({ model.refreshScopes(null) }, model)
    DependencyValidationManager.getInstance(project).addScopeListener({ model.refreshScopes(null) }, model)
    return flow
  }


  private fun subscribeToModelUpdates(model: AbstractScopeModel, modelId: String, project: Project): Flow<SearchScopesInfo> {
    val flow = channelFlow {
      model.addScopeModelListener(object : ScopeModelListener {
        override fun scopesUpdated(scopes: ScopesSnapshot) {
          var scopesState = modelIdToScopes[modelId]
          if (scopesState == null) {
            scopesState = ScopesStateService.getInstance(project).getScopesState()
            modelIdToScopes[modelId] = scopesState
          }
          val scopesStateMap = mutableMapOf<String, ScopeDescriptor>()
          val scopesData = scopes.scopeDescriptors.mapNotNull { descriptor ->
            val scopeId = scopesState.addScope(descriptor)
            val scopeData = SearchScopeData.from(descriptor, scopeId) ?: return@mapNotNull null
            scopesStateMap[scopeData.scopeId] = descriptor
            scopeData
          }
          scopesState.updateScopes(scopesStateMap)

          val searchScopesInfo = SearchScopesInfo(scopesData, null, null, null)
          launch {
            send(searchScopesInfo)
          }
        }
      })

      awaitClose {}
    }
    return flow
  }

  override suspend fun updateModel(modelId: String, scopesInfo: SearchScopesInfo): Flow<SearchScopesInfo> {
    TODO("Not yet implemented")
  }

  override suspend fun dispose(modelId: String) {
    modelIdToScopes.remove(modelId)
    val model = modelIdToModel[modelId] ?: return
    Disposer.dispose(model)
    modelIdToModel.remove(modelId)
  }
}

private class ScopesStateApiProvider : RemoteApiProvider {
  override fun RemoteApiProvider.Sink.remoteApis() {
    remoteApi(remoteApiDescriptor<ScopeModelApi>()) {
      ScopesModelApiImpl()
    }
  }
}