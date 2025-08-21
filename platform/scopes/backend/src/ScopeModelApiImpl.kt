// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.scopes.backend

import com.intellij.ide.ui.WindowFocusFrontendService
import com.intellij.ide.util.scopeChooser.*
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.packageDependencies.DependencyValidationManager
import com.intellij.platform.project.ProjectId
import com.intellij.platform.project.findProjectOrNullWithLogWarn
import com.intellij.platform.rpc.backend.RemoteApiProvider
import com.intellij.platform.scopes.ScopeModelRemoteApi
import com.intellij.platform.scopes.SearchScopeData
import com.intellij.platform.scopes.SearchScopesInfo
import com.intellij.psi.search.scope.packageSet.NamedScopeManager
import fleet.rpc.remoteApiDescriptor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import java.util.*
import java.util.concurrent.ConcurrentHashMap

private val LOG = logger<ScopesModelRemoteApiImpl>()

internal class ScopesModelRemoteApiImpl : ScopeModelRemoteApi {
  private val modelIdToModel = ConcurrentHashMap<String, AbstractScopeModel>()
  private var selectedScopeName: String? = null
    get() {
      val result = field
      field = null
      return result
    }

  override suspend fun createModelAndSubscribe(projectId: ProjectId, modelId: String, filterConditionType: ScopesFilterConditionType): Flow<SearchScopesInfo>? {
    val project = projectId.findProjectOrNullWithLogWarn(LOG) ?: return null
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

  override suspend fun openEditScopesDialog(projectId: ProjectId, selectedScopeId: String?): Deferred<String?> {
    val project = projectId.findProjectOrNullWithLogWarn(LOG) ?: return CompletableDeferred(value = null)
    selectedScopeName = selectedScopeId
    val deferred = CompletableDeferred<String?>()
    val coroutineScope = ScopeModelService.getInstance(project).getCoroutineScope()
    coroutineScope.launch(Dispatchers.EDT) {
      WindowFocusFrontendService.getInstance().performActionWithFocus(true) {
        val dialog = EditScopesDialog.showDialog(project, selectedScopeName)
        if (dialog.isOK) {
          val scopeName = dialog.selectedScope?.scopeId
          val scopeId = scopeName?.let { ScopesStateService.getInstance(project).getIdByScopeName(it) }
          if (scopeId == null) selectedScopeName = scopeName
          deferred.complete(scopeId)
        } else {
          deferred.complete(null)
        }
      }
    }
    return deferred
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

  override suspend fun performScopeSelection(scopeId: String, modelId: String, projectId: ProjectId): Deferred<Unit> {
    val project = projectId.findProjectOrNullWithLogWarn(LOG) ?: return CompletableDeferred(value = Unit)
    val scopesStateService = ScopesStateService.getInstance(project)
    val deferred = CompletableDeferred<Unit>()
    ScopeModelService.getInstance(project).getCoroutineScope().launch {
      scopesStateService.getScopeById(scopeId)
      deferred.complete(Unit)
    }
    return deferred
  }
}

private class ScopesStateApiProvider : RemoteApiProvider {
  override fun RemoteApiProvider.Sink.remoteApis() {
    remoteApi(remoteApiDescriptor<ScopeModelRemoteApi>()) {
      ScopesModelRemoteApiImpl()
    }
  }
}