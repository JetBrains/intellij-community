// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.whatsNew

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.future.asCompletableFuture
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@Service
internal class WhatsNewMultipageIdsCache(coroutineScope: CoroutineScope) {
  private val cachedIds = ConcurrentHashMap.newKeySet<String>()
  private val isLoaded = AtomicBoolean(false)
  private val isFailed = AtomicBoolean(false)
  private val loadFuture: CompletableFuture<Unit>

  init {
    loadFuture = coroutineScope.async(Dispatchers.IO) {
      try {
        loadMultipageIds()
      }
      catch (e: Exception) {
        isFailed.set(true)
        logger.warn("Failed to load multipage IDs", e)
      }
      finally {
        isLoaded.set(true)
      }
    }.asCompletableFuture()
  }

  fun isValidId(id: String): Boolean {
    ensureLoaded()
    return id in cachedIds
  }

  private fun ensureLoaded() {
    if (isLoaded.get()) return

    try {
      val application = ApplicationManager.getApplication()
      if (application.isDispatchThread) {
        return
      }

      loadFuture.get(5, TimeUnit.SECONDS)
    }
    catch (e: Exception) {
      if (isFailed.get()) {
        logger.debug("Multipage IDs load already failed, not waiting")
      }
      else {
        logger.warn("Failed to wait for multipage IDs load", e)
      }
    }
  }

  private suspend fun loadMultipageIds() {
    val provider = WhatsNewInVisionContentProvider.getInstance()
    if (provider.isAvailable()) {
      val content = provider.getContent()
      val ids = content.entities.flatMap { entity ->
        WhatsNewVisionContent(provider, entity).multipageIds
      }
      cachedIds.addAll(ids)
    }
  }

  companion object {
    fun getInstance(): WhatsNewMultipageIdsCache = service()
  }
}

private val logger = logger<WhatsNewMultipageIdsCache>()