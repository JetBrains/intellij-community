// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.backend.workspace.GlobalWorkspaceModelCache
import com.intellij.platform.util.coroutines.forEachConcurrent
import com.intellij.platform.workspace.jps.serialization.impl.ApplicationLevelUrlRelativizer
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.impl.isConsistent
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.milliseconds

@OptIn(FlowPreview::class)
internal class GlobalWorkspaceModelCacheImpl(coroutineScope: CoroutineScope) : GlobalWorkspaceModelCache {
  private val saveRequests = MutableSharedFlow<Unit>(replay=1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
  private val cacheFiles: ConcurrentHashMap<String, Path> = ConcurrentHashMap()

  override fun cacheFile(id: GlobalWorkspaceModelCache.InternalEnvironmentName): Path {
    return cacheFiles[id.name]
           ?: throw IllegalArgumentException("Global workspace storage with id $id must be registered with `registerCachePartition` before it can be loaded")
  }

  private lateinit var virtualFileUrlManager: VirtualFileUrlManager

  private val urlRelativizer =
    if (Registry.`is`("ide.workspace.model.store.relative.paths.in.cache", false)) {
      ApplicationLevelUrlRelativizer(insideIdeProcess = true)
    } else {
      null
    }

  private val cacheSerializer by lazy {
    if (!::virtualFileUrlManager.isInitialized) {
      throw UninitializedPropertyAccessException("VirtualFileUrlManager was not initialized. Please call `GlobalWorkspaceModelCache.setVirtualFileUrlManager` before any other methods.")
    }
    WorkspaceModelCacheSerializer(virtualFileUrlManager, urlRelativizer)
  }

  init {
    LOG.debug("Global Model Cache")

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

  override suspend fun saveCacheNow() {
    doCacheSaving()
  }

  override fun invalidateCaches() {
    Companion.invalidateCaches()
  }

  private suspend fun doCacheSaving() {
    cacheFiles.entries.forEachConcurrent { (id, cacheFile) ->
      val storage = GlobalWorkspaceModel.getInstanceByInternalName(InternalEnvironmentNameImpl(id)).currentSnapshot
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
  }

  override fun loadCache(id: GlobalWorkspaceModelCache.InternalEnvironmentName): MutableEntityStorage? {
    if (ApplicationManager.getApplication().isUnitTestMode && !System.getProperty("ide.tests.permit.global.workspace.model.serialization", "false").toBoolean()) return null
    val cacheFile = cacheFile(id)
    return cacheSerializer.loadCacheFromFile(cacheFile, invalidateCachesMarkerFile, invalidateCachesMarkerFile)
  }

  override fun setVirtualFileUrlManager(vfuManager: VirtualFileUrlManager) {
    virtualFileUrlManager = vfuManager
  }

  override fun registerCachePartition(id: GlobalWorkspaceModelCache.InternalEnvironmentName) {
    val cacheSuffix = if (id.name == GlobalWorkspaceModelRegistry.GLOBAL_WORKSPACE_MODEL_LOCAL_CACHE_ID) "$DATA_DIR_NAME/cache.data" else "$DATA_DIR_NAME/${id.name}/cache.data"
    val path = PathManager.getSystemDir().resolve(cacheSuffix)
    cacheFiles[id.name] = path
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