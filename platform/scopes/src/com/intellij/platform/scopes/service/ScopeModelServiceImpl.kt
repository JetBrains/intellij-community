// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.scopes.service

import com.intellij.ide.util.scopeChooser.ScopeDescriptor
import com.intellij.ide.util.scopeChooser.ScopeModelService
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

  override fun loadItemsAsync(modelId: String, onFinished: suspend (Map<String, ScopeDescriptor>?) -> Unit) {
    coroutineScope.childScope("ScopesStateService.subscribeToScopeStates").launch {
      try {
        val scopesFlow = ScopeModelApi.getInstance().createModelAndSubscribe(project.projectId(), modelId)
        if (scopesFlow == null) {
          LOG.warn("Failed to subscribe to model updates for modelId: $modelId")
          onFinished(null)
          return@launch
        }
        scopesFlow.collect { scopesInfo ->
          val fetchedScopes = scopesInfo.getScopeDescriptors()
          onFinished(fetchedScopes)
          ScopesStateService.getInstance(project).getScopesState().updateIfNotExists(fetchedScopes)
          scopeIdToDescriptor = fetchedScopes
        }
      }
      catch (e: RpcTimeoutException) {
        LOG.warn("Failed to subscribe to model updates for modelId: $modelId", e)
        onFinished(null)
      }
    }
  }

  override fun disposeModel(modelId: String) {
    coroutineScope.launch {
      try {
        ScopeModelApi.getInstance().dispose(modelId)
      }
      catch (e: RpcTimeoutException) {
        LOG.warn("Failed to dispose model for modelId: $modelId", e)
      }
    }
  }

  override fun getScopeById(scopeId: String): ScopeDescriptor? {
    scopeIdToDescriptor.get(scopeId)?.let { return it }
    return null
  }
}