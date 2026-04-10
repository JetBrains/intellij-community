// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.whatsNew

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.milliseconds

@Service
internal class WhatsNewMultipageIdsCache(coroutineScope: CoroutineScope) {
  internal val cachedIds = ConcurrentHashMap.newKeySet<String>()
  private val isLoaded = CompletableDeferred<Unit>()

  init {
    coroutineScope.launch(Dispatchers.IO) {
      try {
        loadMultipageIds()
      }
      finally {
        isLoaded.complete(Unit)
      }
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
    }
    catch (e: Exception) {
      logger.warn("Failed to load multipage IDs", e)
    }
  }

  fun isValidId(id: String): Boolean = id in cachedIds

  suspend fun waitUntilLoaded(timeoutMs: Long) {
    withTimeoutOrNull(timeoutMs.milliseconds) {
      isLoaded.await()
    }
  }

  companion object {
    fun getInstance(): WhatsNewMultipageIdsCache = service()
  }
}

private val logger = logger<WhatsNewMultipageIdsCache>()