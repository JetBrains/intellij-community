// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.backend.workspace.GlobalWorkspaceModelCache
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.impl.isConsistent
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager
import com.intellij.workspaceModel.ide.getGlobalInstance
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.milliseconds

@OptIn(FlowPreview::class)
internal class GlobalWorkspaceModelCacheImpl(coroutineScope: CoroutineScope) : GlobalWorkspaceModelCache {
  private val saveRequests = MutableSharedFlow<Unit>(replay=1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
  private val cacheFile by lazy { PathManager.getSystemDir().resolve("$DATA_DIR_NAME/cache.data") }

  private val urlRelativizer =
    if (Registry.`is`("ide.workspace.model.store.relative.paths.in.cache", true)) {
      ApplicationLevelUrlRelativizer()
    } else {
      null
    }

  private val cacheSerializer = WorkspaceModelCacheSerializer(VirtualFileUrlManager.getGlobalInstance(), urlRelativizer)

  init {
    LOG.debug("Global Model Cache at $cacheFile")

    coroutineScope.launch {
      saveRequests
        .debounce(1_000.milliseconds)
        .collect {
          withContext(Dispatchers.IO) {
            doCacheSaving()
          }
        }
    }
  }

  override fun scheduleCacheSave() {
    if (ApplicationManager.getApplication().isUnitTestMode) {
      return
    }

    LOG.debug("Schedule cache update")
    check(saveRequests.tryEmit(Unit))
  }

  override fun invalidateCaches() {
    Companion.invalidateCaches()
  }

  private suspend fun doCacheSaving() {
    val storage = GlobalWorkspaceModel.getInstance().currentSnapshot
    if (!storage.isConsistent) {
      invalidateCaches()
    }

    withContext(Dispatchers.IO) {
      if (!cachesInvalidated.get()) {
        cacheSerializer.saveCacheToFile(storage, cacheFile)
      }
      else {
        Files.deleteIfExists(cacheFile)
      }
    }
  }

  override fun loadCache(): MutableEntityStorage? {
    if (ApplicationManager.getApplication().isUnitTestMode) return null
    return cacheSerializer.loadCacheFromFile(cacheFile, invalidateCachesMarkerFile, invalidateCachesMarkerFile)
  }

  companion object {
    private val LOG = logger<GlobalWorkspaceModelCache>()
    internal const val DATA_DIR_NAME: String = "global-model-cache"

    private val cachesInvalidated = AtomicBoolean(false)
    private val invalidateCachesMarkerFile by lazy { PathManager.getSystemDir().resolve("$DATA_DIR_NAME/.invalidate") }

    internal fun invalidateCaches() {
      LOG.info("Invalidating global caches by creating $invalidateCachesMarkerFile")
      invalidateCaches(cachesInvalidated, invalidateCachesMarkerFile)
    }
  }
}