// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.scopes.service

import com.intellij.ide.rpc.performRpcWithRetries
import com.intellij.ide.rpc.rpcId
import com.intellij.ide.util.scopeChooser.ScopeDescriptor
import com.intellij.ide.util.scopeChooser.ScopeModelService
import com.intellij.ide.util.scopeChooser.ScopesFilterConditionType
import com.intellij.ide.util.scopeChooser.ScopesStateService
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.platform.project.projectId
import com.intellij.platform.scopes.ScopeModelRemoteApi
import com.intellij.platform.util.coroutines.childScope
import com.intellij.util.cancelOnDispose
import fleet.rpc.client.RpcTimeoutException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.await

private val LOG = logger<ScopeModelServiceImpl>()

@ApiStatus.Internal
internal class ScopeModelServiceImpl(private val project: Project, private val coroutineScope: CoroutineScope) : ScopeModelService {
  private var scopeIdToDescriptor = mapOf<String, ScopeDescriptor>()
  private var itemsLoadingJob: Job? = null
  private var editScopesJob: Job? = null

  override fun loadItemsAsync(
    modelId: String,
    filterConditionType: ScopesFilterConditionType,
    dataContextPromise: Promise<DataContext>,
    onScopesUpdate: suspend (Map<String, ScopeDescriptor>?, selectedScopeId: String?) -> Unit,
  ) {
    itemsLoadingJob = coroutineScope.childScope("ScopesStateService.subscribeToScopeStates").launch {
      val dataContext = dataContextPromise.await()
      LOG.performRpcWithRetries {
        val scopesFlow = ScopeModelRemoteApi.getInstance().createModelAndSubscribe(
          project.projectId(), modelId, filterConditionType, dataContext.rpcId())
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
    editScopesJob?.cancel()
  }

  override fun getScopeDescriptorById(scopeId: String): ScopeDescriptor? {
    scopeIdToDescriptor[scopeId]?.let { return it }
    return null
  }

  override fun openEditScopesDialog(selectedScopeId: String?, modelId: String, onFinish: (selectedScopeId: String?) -> Unit) {
    val projectId = project.projectId()
    editScopesJob = coroutineScope.launch {
      val deferred = try {
        ScopeModelRemoteApi.getInstance().openEditScopesDialog(projectId, selectedScopeId, modelId)
      }
      catch (e: RpcTimeoutException) {
        LOG.warn("Failed to edit scopes", e)
        null
      }
      deferred?.cancelOnDispose(project)
      deferred?.invokeOnCompletion { cause ->
        if (cause != null) {
          onFinish(null)
        }
      }
      onFinish(deferred?.await())
    }
  }

  override fun getCoroutineScope(): CoroutineScope {
    return coroutineScope
  }
}