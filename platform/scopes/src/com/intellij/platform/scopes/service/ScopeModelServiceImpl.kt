// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.scopes.service

import com.intellij.ide.rpc.performRpcWithRetries
import com.intellij.ide.util.scopeChooser.ScopeDescriptor
import com.intellij.ide.util.scopeChooser.ScopeModelService
import com.intellij.ide.util.scopeChooser.ScopesFilterConditionType
import com.intellij.ide.util.scopeChooser.ScopesStateService
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.platform.project.projectId
import com.intellij.platform.project.projectIdOrNullWithLogError
import com.intellij.platform.scopes.ScopeModelApi
import com.intellij.platform.util.coroutines.childScope
import fleet.rpc.client.RpcTimeoutException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus


private val LOG = logger<ScopeModelServiceImpl>()

@ApiStatus.Internal
private class ScopeModelServiceImpl(private val project: Project, private val coroutineScope: CoroutineScope) : ScopeModelService {
  private var scopeIdToDescriptor = mapOf<String, ScopeDescriptor>()
  private var itemsLoadingJob: Job? = null
  private var editScopesJob: Job? = null

  override fun loadItemsAsync(modelId: String, filterConditionType: ScopesFilterConditionType, onScopesUpdate: suspend (Map<String, ScopeDescriptor>?, selectedScopeId: String?) -> Unit) {
    itemsLoadingJob = coroutineScope.childScope("ScopesStateService.subscribeToScopeStates").launch {
      LOG.performRpcWithRetries {
        val scopesFlow =
          ScopeModelApi.getInstance().createModelAndSubscribe(project.projectId(), modelId, filterConditionType)
        if (scopesFlow == null) {
          LOG.error("Failed to subscribe to model updates for modelId: $modelId")
          onScopesUpdate(null, null)
          return@performRpcWithRetries
        }
        scopesFlow.collect { scopesInfo ->
          val fetchedScopes = scopesInfo.getScopeDescriptors()
          onScopesUpdate(fetchedScopes, scopesInfo.selectedScopeId)
          ScopesStateService.getInstance(project).getScopesState().updateIfNotExists(fetchedScopes)
          scopeIdToDescriptor = fetchedScopes
        }
      }
    }
  }

  override fun disposeModel(modelId: String) {
    itemsLoadingJob?.cancel()
  }

  override fun getScopeById(scopeId: String): ScopeDescriptor? {
    scopeIdToDescriptor[scopeId]?.let { return it }
    return null
  }

  override fun openEditScopesDialog(selectedScopeId: String?, onFinish: (selectedScopeId: String?) -> Unit) {
    val projectId = project.projectIdOrNullWithLogError(LOG) ?: return
    editScopesJob = coroutineScope.launch {
      try {
        val selectedScopeName = selectedScopeId?.let { ScopesStateService.getInstance(project).getScopeById(selectedScopeId)?.displayName }
        val scopeId = ScopeModelApi.getInstance().openEditScopesDialog(projectId, selectedScopeName)
        onFinish(scopeId)
      }
      catch (e: RpcTimeoutException) {
        LOG.warn("Failed to edit scopes", e)
        onFinish(null)
      }
    }
  }
}