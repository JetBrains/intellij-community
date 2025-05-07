// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.pluginManager.frontend

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.marketplace.IdeCompatibleUpdate
import com.intellij.ide.plugins.marketplace.IntellijUpdateMetadata
import com.intellij.ide.plugins.marketplace.MarketplaceSearchPluginData
import com.intellij.ide.plugins.marketplace.PluginReviewComment
import com.intellij.ide.plugins.newui.PluginUiModel
import com.intellij.ide.plugins.newui.UiPluginManagerController
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.platform.pluginManager.shared.rpc.PluginManagerApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class BackendUiPluginManagerController() : UiPluginManagerController {
  override fun getPlugins(): List<PluginUiModel> {
    return awaitForResult { PluginManagerApi.getInstance().getPlugins() }
  }

  override fun getVisiblePlugins(showImplementationDetails: Boolean): List<PluginUiModel> {
    return awaitForResult { PluginManagerApi.getInstance().getVisiblePlugins(showImplementationDetails) }
  }

  override fun getInstalledPlugins(): List<PluginUiModel> {
    return awaitForResult { PluginManagerApi.getInstance().getInstalledPlugins() }
  }

  override fun isPluginDisabled(pluginId: PluginId): Boolean {
    return awaitForResult { PluginManagerApi.getInstance().isPluginDisabled(pluginId) }
  }

  override fun executeMarketplaceQuery(query: String, count: Int, includeIncompatible: Boolean): List<MarketplaceSearchPluginData> {
    return awaitForResult { PluginManagerApi.getInstance().executeMarketplaceQuery(query, count, includeIncompatible) }
  }

  override fun loadUpdateMetadata(xmlId: String, ideCompatibleUpdate: IdeCompatibleUpdate, indicator: ProgressIndicator?): IntellijUpdateMetadata {
    return awaitForResult { PluginManagerApi.getInstance().loadMetadata(xmlId, ideCompatibleUpdate) }
  }

  override fun loadPluginReviews(pluginId: PluginId, page: Int): List<PluginReviewComment>? {
    return awaitForResult { PluginManagerApi.getInstance().loadPluginReviews(pluginId, page) }
  }

  @Deprecated("Test method ")
  fun <T> awaitForResult(body: suspend () -> T): T {
    val deferred = CompletableDeferred<T>()
    service<BackendRpcCoroutineContext>().coroutineScope.launch {
      deferred.complete(body())
    }
    return runBlocking { deferred.await() }
  }
}


@Service
@ApiStatus.Internal
class BackendRpcCoroutineContext(val coroutineScope: CoroutineScope)