// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.scopes.backend

import com.intellij.ide.util.scopeChooser.*
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.packageDependencies.DependencyValidationManager
import com.intellij.platform.project.ProjectId
import com.intellij.platform.project.findProjectOrNullWithLogError
import com.intellij.platform.rpc.backend.RemoteApiProvider
import com.intellij.platform.scopes.ScopeModelApi
import com.intellij.platform.scopes.SearchScopeData
import com.intellij.platform.scopes.SearchScopesInfo
import com.intellij.psi.search.scope.packageSet.NamedScopeManager
import fleet.rpc.remoteApiDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*
import java.util.concurrent.ConcurrentHashMap

private val LOG = logger<ScopesModelApiImpl>()

internal class ScopesModelApiImpl : ScopeModelApi {
  private val modelIdToModel = ConcurrentHashMap<String, AbstractScopeModel>()
  private var selectedScopeName: String? = null
    get() {
      val result = field
      field = null
      return result
    }

  override suspend fun createModelAndSubscribe(projectId: ProjectId, modelId: String, filterConditionType: ScopesFilterConditionType): Flow<SearchScopesInfo>? {
    val project = projectId.findProjectOrNullWithLogError(LOG) ?: return null
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
    val scopeFilter = filterConditionType.getScopeFilterByType()
    if (scopeFilter != null) model.setFilter(scopeFilter)

    NamedScopeManager.getInstance(project).addScopeListener({ model.refreshScopes(null) }, model)
    DependencyValidationManager.getInstance(project).addScopeListener({ model.refreshScopes(null) }, model)
    return flow
  }

  override suspend fun openEditScopesDialog(projectId: ProjectId, selectedScopeId: String?): String? {
    val project = projectId.findProjectOrNullWithLogError(LOG) ?: return null
    return withContext(Dispatchers.EDT) {
      val dialog = EditScopesDialog.showDialog(project, selectedScopeName)
      if (dialog.isOK) {
        val scopeName = dialog.selectedScope?.scopeId
        val scopeId = scopeName?.let { ScopesStateService.getInstance(project).getIdByScopeName(it) }
         if (scopeId == null) selectedScopeName = scopeName
        return@withContext scopeId
      }
      return@withContext null
    }
  }

  private fun subscribeToModelUpdates(model: AbstractScopeModel, modelId: String, project: Project): Flow<SearchScopesInfo> {
    val flow = channelFlow {
      model.addScopeModelListener(object : ScopeModelListener {
        override fun scopesUpdated(scopes: ScopesSnapshot) {
          val scopesState = ScopesStateService.getInstance(project).getScopesState()
          val scopesStateMap = mutableMapOf<String, ScopeDescriptor>()
          val scopesData = scopes.scopeDescriptors.mapNotNull { descriptor ->
            val scopeId = scopesState.addScope(descriptor)
            val scopeData = SearchScopeData.from(descriptor, scopeId) ?: return@mapNotNull null
            scopesStateMap[scopeData.scopeId] = descriptor
            scopeData
          }
          scopesState.updateScopes(scopesStateMap)

          val currentScopeName = selectedScopeName
          val currentScopeId = currentScopeName?.let { name -> scopesStateMap.entries.find { it.value.displayName == name }?.key }
          val searchScopesInfo = SearchScopesInfo(scopesData, currentScopeId, null, null)
          launch {
            send(searchScopesInfo)
          }
        }
      })

      awaitClose {
        val model = modelIdToModel[modelId]
        model?.let { Disposer.dispose(it) }
        modelIdToModel.remove(modelId)
      }
    }
    return flow
  }

  override suspend fun performScopeSelection(scopeId: String, projectId: ProjectId): Boolean {
    val project = projectId.findProjectOrNullWithLogError(LOG)
    if (project == null) {
      LOG.warn("Cannot find project for id $projectId")
      return false
    }
    return ScopesStateService.getInstance(project).getScopeById(scopeId) != null
  }
}

private class ScopesStateApiProvider : RemoteApiProvider {
  override fun RemoteApiProvider.Sink.remoteApis() {
    remoteApi(remoteApiDescriptor<ScopeModelApi>()) {
      ScopesModelApiImpl()
    }
  }
}