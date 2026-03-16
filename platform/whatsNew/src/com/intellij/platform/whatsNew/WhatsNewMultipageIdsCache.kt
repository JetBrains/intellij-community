// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.whatsNew

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

@Service
internal class WhatsNewMultipageIdsCache(coroutineScope: CoroutineScope) {
  internal val cachedIds = ConcurrentHashMap.newKeySet<String>()

  init {
    coroutineScope.launch(Dispatchers.IO) {
      loadMultipageIds()
    }
  }

  private suspend fun loadMultipageIds() {
    try {
      val provider = WhatsNewInVisionContentProvider.getInstance()
      if (provider.isAvailable()) {
        val content = provider.getContent()
        val ids = content.entities.flatMap { entity ->
          WhatsNewVisionContent(provider, entity).multipageIds
        }
        cachedIds.addAll(ids)
      }
    } catch (e: Exception) {
      logger.warn("Failed to load multipage IDs", e)
    }
  }

  fun isValidId(id: String): Boolean = id in cachedIds

  companion object {
    fun getInstance(): WhatsNewMultipageIdsCache = service()
  }
}

private val logger = logger<WhatsNewMultipageIdsCache>()