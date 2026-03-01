// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.impl.diagnostic.errorsDialog

import com.intellij.diagnostic.AbstractMessage
import com.intellij.diagnostic.DefaultIdeaErrorLogger
import com.intellij.diagnostic.IdeErrorsDialog.Companion.hashMessage
import com.intellij.diagnostic.MessagePool
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.PluginUtil
import com.intellij.ide.plugins.newui.PluginUiModel
import com.intellij.ide.plugins.newui.UiPluginManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.ProblematicPluginInfo
import com.intellij.openapi.diagnostic.ProblematicPluginInfoBasedOnDescriptor
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.NlsSafe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async

@Service(Service.Level.APP)
internal class ErrorMessageClustering(private val coroutineScope: CoroutineScope) {
  companion object {
    fun getInstance(): ErrorMessageClustering = service<ErrorMessageClustering>()
  }

  internal fun clusterMessages(): Deferred<List<ErrorMessageCluster>> {
    return coroutineScope.async {
      val messages = MessagePool.getInstance().getFatalErrors(true, true)
      messages
        .groupBy { hashMessage(it) }
        .map { createCluster(it.value) }
    }
  }

  private suspend fun createCluster(messages: List<AbstractMessage>): ErrorMessageCluster {
    val first = messages.first()
    val pluginId = PluginUtil.getInstance().findPluginId(first.throwable)
    val plugin = createPluginInfo(pluginId)
    val submitter = DefaultIdeaErrorLogger.findSubmitterByPluginInfo(first.throwable, plugin)
    return ErrorMessageCluster(messages, pluginId, plugin, submitter)
  }

  private suspend fun createPluginInfo(pluginId: PluginId?): ProblematicPluginInfo? {
    if (pluginId == null) return null
    val localPlugin = PluginManagerCore.getPlugin(pluginId)
    if (localPlugin != null) return ProblematicPluginInfoBasedOnDescriptor(localPlugin)
    val uiModel = UiPluginManager.getInstance().getPlugin(pluginId) ?: return null
    return ProblematicPluginInfoBasedOnModel(uiModel)
  }
}

private class ProblematicPluginInfoBasedOnModel(val plugin: PluginUiModel) : ProblematicPluginInfo {
  override val pluginId: PluginId
    get() = plugin.pluginId
  override val isBundled: Boolean
    get() = plugin.isBundled
  override val allowsBundledUpdate: Boolean
    get() = plugin.allowBundledUpdate
  override val name: @NlsSafe String
    get() = plugin.name ?: pluginId.idString
  override val version: @NlsSafe String?
    get() = plugin.version
  override val organization: @NlsSafe String?
    get() = plugin.organization
  override val vendor: @NlsSafe String?
    get() = plugin.vendor
  override val vendorUrl: String?
    get() = plugin.vendorDetails?.url
  override val vendorEmail: String?
    get() = null
}