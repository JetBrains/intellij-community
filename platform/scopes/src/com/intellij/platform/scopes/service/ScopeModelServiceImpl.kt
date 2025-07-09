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
import com.intellij.platform.scopes.ScopeModelApi
import com.intellij.platform.util.coroutines.childScope
import fleet.rpc.client.RpcTimeoutException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus


private val LOG = logger<ScopeModelServiceImpl>()

@ApiStatus.Internal
private class ScopeModelServiceImpl(private val project: Project, private val coroutineScope: CoroutineScope) : ScopeModelService {
  private var scopeIdToDescriptor = mapOf<String, ScopeDescriptor>()
  private var itemsLoadingJob: kotlinx.coroutines.Job? = null

  override fun loadItemsAsync(modelId: String, filterConditionType: ScopesFilterConditionType, onScopesUpdate: suspend (Map<String, ScopeDescriptor>?) -> Unit) {
    itemsLoadingJob = coroutineScope.childScope("ScopesStateService.subscribeToScopeStates").launch {
      LOG.performRpcWithRetries {
        val scopesFlow = try {
          ScopeModelApi.getInstance().createModelAndSubscribe(project.projectId(), modelId, filterConditionType)
        }
        catch (e: RpcTimeoutException) {
          LOG.error("Failed to subscribe to model updates for modelId: $modelId", e)
          onScopesUpdate(null)
          return@performRpcWithRetries
        }
        if (scopesFlow == null) {
          LOG.error("Failed to subscribe to model updates for modelId: $modelId")
          onScopesUpdate(null)
          return@performRpcWithRetries
        }
        scopesFlow.collect { scopesInfo ->
          val fetchedScopes = scopesInfo.getScopeDescriptors()
          onScopesUpdate(fetchedScopes)
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
}