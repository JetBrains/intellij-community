// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.scopes.backend

import com.intellij.ide.rpc.DataContextId
import com.intellij.ide.rpc.dataContext
import com.intellij.ide.ui.WindowFocusFrontendService
import com.intellij.ide.util.scopeChooser.AbstractScopeModel
import com.intellij.ide.util.scopeChooser.EditScopesDialog
import com.intellij.ide.util.scopeChooser.ScopeDescriptor
import com.intellij.ide.util.scopeChooser.ScopeModelListener
import com.intellij.ide.util.scopeChooser.ScopeModelService
import com.intellij.ide.util.scopeChooser.ScopeOption
import com.intellij.ide.util.scopeChooser.ScopeService
import com.intellij.ide.util.scopeChooser.ScopesFilterConditionType
import com.intellij.ide.util.scopeChooser.ScopesSnapshot
import com.intellij.ide.util.scopeChooser.ScopesStateService
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.packageDependencies.DependencyValidationManager
import com.intellij.platform.project.ProjectId
import com.intellij.platform.project.findProjectOrNull
import com.intellij.platform.rpc.backend.RemoteApiProvider
import com.intellij.platform.scopes.ScopeModelRemoteApi
import com.intellij.platform.scopes.SearchScopeData
import com.intellij.platform.scopes.SearchScopesInfo
import com.intellij.psi.search.scope.packageSet.NamedScopeManager
import com.intellij.util.cancelOnDispose
import fleet.rpc.remoteApiDescriptor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.EnumSet
import java.util.concurrent.ConcurrentHashMap

internal class ScopesModelRemoteApiImpl : ScopeModelRemoteApi {
  private val modelIdToModel = ConcurrentHashMap<String, AbstractScopeModel>()
  /**
   * Tracks newly created scope names by model ID for deferred selection.
   *
   * This is necessary because when a scope is created or renamed using EditScopeDialog,
   * the scope isn't immediately available in the scopes list. The dialog only sends an event
   * for the scope change. After scopes update asynchronously, we can then select the newly
   * created/renamed scope using its name.
   */
  private val modelIdToSelectedScopeName = ConcurrentHashMap<String, String>()

  override suspend fun createModelAndSubscribe(
    projectId: ProjectId,
    modelId: String,
    filterConditionType: ScopesFilterConditionType,
    dataContextId: DataContextId?
  ): Flow<SearchScopesInfo>? {
    val project = projectId.findProjectOrNull() ?: return null
    val model = project.getService(ScopeService::class.java)
      .createModel(EnumSet.of(
        ScopeOption.FROM_SELECTION,
        ScopeOption.USAGE_VIEW,
        ScopeOption.LIBRARIES,
        ScopeOption.SEARCH_RESULTS
      ))
    modelIdToModel[modelId] = model
    val flow = subscribeToModelUpdates(model, modelId, project)
    val dataContext = withContext(Dispatchers.EDT) { dataContextId?.dataContext() }
    model.refreshScopes(dataContext)
    val scopeFilter = filterConditionType.getScopeFilterByType()
    if (scopeFilter != null) model.setFilter(scopeFilter)

    NamedScopeManager.getInstance(project).addScopeListener({ model.refreshScopes(null) }, model)
    DependencyValidationManager.getInstance(project).addScopeListener({ model.refreshScopes(null) }, model)
    return flow
  }

  override suspend fun openEditScopesDialog(projectId: ProjectId, selectedScopeId: String?, modelId: String): Deferred<String?> {
    val project = projectId.findProjectOrNull() ?: return CompletableDeferred(value = null)
    val deferred = CompletableDeferred<String?>()
    deferred.cancelOnDispose(project)
    val coroutineScope = ScopeModelService.getInstance(project).getCoroutineScope()
    coroutineScope.launch(Dispatchers.EDT) {
      WindowFocusFrontendService.getInstance().performActionWithFocus(true) {
        val dialog = EditScopesDialog.showDialog(project, selectedScopeId?.let { ScopesStateService.getInstance(project).getScopeNameById(it) })
        if (dialog.isOK) {
          val scopeName = dialog.selectedScope?.scopeId
          val scopeId = scopeName?.let { ScopesStateService.getInstance(project).getIdByScopeName(it) }

          // If the scope was created/renamed but not yet registered with an ID,
          // store its name for selection after the next scope refresh
          if (scopeId == null && scopeName != null) modelIdToSelectedScopeName[modelId] = scopeName

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

          val currentScopeName = modelIdToSelectedScopeName[modelId]
          if (currentScopeName != null) { modelIdToSelectedScopeName.remove(modelId) }
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
        modelIdToSelectedScopeName.remove(modelId)
      }
    }
    return flow
  }

  override suspend fun performScopeSelection(scopeId: String, projectId: ProjectId): Deferred<Unit> {
    val project = projectId.findProjectOrNull() ?: return CompletableDeferred(value = Unit)
    val scopesStateService = ScopesStateService.getInstance(project)
    val deferred = CompletableDeferred<Unit>()
    deferred.cancelOnDispose(project)
    ScopeModelService.getInstance(project).getCoroutineScope().launch {
      scopesStateService.getScopeById(scopeId)
      deferred.complete(Unit)
    }
    return deferred
  }
}

internal class ScopesStateApiProvider : RemoteApiProvider {
  override fun RemoteApiProvider.Sink.remoteApis() {
    remoteApi(remoteApiDescriptor<ScopeModelRemoteApi>()) {
      ScopesModelRemoteApiImpl()
    }
  }
}