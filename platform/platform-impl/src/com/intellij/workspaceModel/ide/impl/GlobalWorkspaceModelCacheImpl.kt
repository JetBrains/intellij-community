// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.util.SingleAlarm
import com.intellij.workspaceModel.ide.*
import com.intellij.workspaceModel.storage.*
import com.intellij.workspaceModel.storage.impl.isConsistent
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicBoolean

@ApiStatus.Internal
class GlobalWorkspaceModelCacheImpl : AbstractWorkspaceModelCache(), GlobalWorkspaceModelCache, Disposable {
  private val saveAlarm = SingleAlarm.pooledThreadSingleAlarm(1000, this) { this.doCacheSaving() }
  private val cacheFile by lazy { PathManager.getConfigDir().resolve("$DATA_DIR_NAME/cache.data") }

  init {
    LOG.debug("Global Model Cache at $cacheFile")
  }

  override fun scheduleCacheSave() {
    if (ApplicationManager.getApplication().isUnitTestMode) return
    LOG.debug("Schedule cache update")
    saveAlarm.request()
  }

  private fun doCacheSaving() {
    val storage = GlobalWorkspaceModel.getInstance().currentSnapshot
    if (!storage.isConsistent) {
      invalidateCaches()
    }

    if (!cachesInvalidated.get()) {
      saveCache(storage, cacheFile)
    }
    else {
      Files.deleteIfExists(cacheFile)
    }
  }

  override fun loadCache(): EntityStorage? = loadCache(cacheFile, invalidateCachesMarkerFile, invalidateCachesMarkerFile)
  override fun dispose() = Unit

  companion object {
    private val LOG = logger<GlobalWorkspaceModelCache>()
    internal const val DATA_DIR_NAME = "global-model-cache"

    private val cachesInvalidated = AtomicBoolean(false)
    private val invalidateCachesMarkerFile by lazy { PathManager.getConfigDir().resolve("$DATA_DIR_NAME/.invalidate") }

    internal fun invalidateCaches() {
      LOG.info("Invalidating global caches by creating $invalidateCachesMarkerFile")
      invalidateCaches(cachesInvalidated, invalidateCachesMarkerFile)
    }

    fun getInstance(): GlobalWorkspaceModelCache = ApplicationManager.getApplication().service()
  }
}