// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import java.util.concurrent.ConcurrentHashMap

@Service()
class PluginDownloadersHolder {
  private val downloaders = ConcurrentHashMap<String, PluginDownloaders>()

  fun getDownloader(sessionId: String, pluginId: String): PluginDownloader? {
    return downloaders[sessionId]?.get(pluginId)
  }

  fun getDownloaders(sessionId: String): List<PluginDownloader> = downloaders[sessionId]?.values?.toList() ?: emptyList()

  fun registerDownloader(sessionId: String, pluginId: String, downloader: PluginDownloader) {
    downloaders.getOrPut(sessionId) { ConcurrentHashMap<String, PluginDownloader>() }[pluginId] = downloader
  }

  fun deleteSession(sessionId: String) {
    downloaders.remove(sessionId)
  }


  fun registerDownloaders(sessionId: String, downloaders: List<PluginDownloader>) {
    downloaders.forEach { registerDownloader(sessionId, it.descriptor.pluginId.idString, it) }
  }

  companion object {
    @JvmStatic
    fun getInstance(): PluginDownloadersHolder = service()
  }
}

typealias PluginDownloaders = ConcurrentHashMap<String, PluginDownloader>