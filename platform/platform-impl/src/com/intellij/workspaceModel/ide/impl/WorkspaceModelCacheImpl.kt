// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.getProjectDataPath
import com.intellij.openapi.project.projectsDataDir
import com.intellij.openapi.util.Disposer
import com.intellij.util.SingleAlarm
import com.intellij.workspaceModel.ide.*
import com.intellij.workspaceModel.storage.EntityStorage
import com.intellij.workspaceModel.storage.EntityStorageSnapshot
import com.intellij.workspaceModel.storage.VersionedStorageChange
import com.intellij.workspaceModel.storage.impl.isConsistent
import com.intellij.workspaceModel.storage.url.VirtualFileUrlManager
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.io.path.exists

@ApiStatus.Internal
class WorkspaceModelCacheImpl(private val project: Project) : WorkspaceModelCache, Disposable {
  override val enabled = forceEnableCaching || !ApplicationManager.getApplication().isUnitTestMode
  private val saveAlarm = SingleAlarm.pooledThreadSingleAlarm(1000, this) { this.doCacheSaving() }

  private val cacheFile by lazy { initCacheFile() }
  private val unloadedEntitiesCacheFile by lazy { project.getProjectDataPath(DATA_DIR_NAME).resolve("unloaded-entities-cache.data") }
  private val invalidateProjectCacheMarkerFile by lazy { project.getProjectDataPath(DATA_DIR_NAME).resolve(".invalidate") }
  private val cacheSerializer = WorkspaceModelCacheSerializer(VirtualFileUrlManager.getInstance(project))

  init {
    if (enabled) {
      LOG.debug("Project Model Cache at $cacheFile")

      project.messageBus.connect(this).subscribe(WorkspaceModelTopics.CHANGED, object : WorkspaceModelChangeListener {
        override fun changed(event: VersionedStorageChange) {
          LOG.debug("Schedule cache update")
          saveAlarm.request()
        }
      })
    }
  }

  private fun initCacheFile(): Path {
    if (ApplicationManager.getApplication().isUnitTestMode && testCacheFile != null) {
      // For testing purposes
      val testFile = testCacheFile!!
      if (!testFile.exists()) {
        error("Test cache file defined, but doesn't exist")
      }
      return testFile
    }

    return project.getProjectDataPath(DATA_DIR_NAME).resolve("cache.data")
  }


  override fun saveCacheNow() {
    saveAlarm.cancel()
    doCacheSaving()
  }

  private fun doCacheSaving() {
    val storage = WorkspaceModel.getInstance(project).currentSnapshot
    val unloadedStorage = WorkspaceModel.getInstance(project).currentSnapshotOfUnloadedEntities
    if (!storage.isConsistent || !unloadedStorage.isConsistent) {
      invalidateProjectCache()
    }

    if (!cachesInvalidated.get()) {
      LOG.debug("Saving project model cache to $cacheFile")
      cacheSerializer.saveCacheToFile(storage, cacheFile, userPreProcessor = true)
      //todo check that where are no entities in the storage instead
      if (unloadedStorage != EntityStorageSnapshot.empty()) {
        LOG.debug("Saving project model cache to $unloadedEntitiesCacheFile")
        cacheSerializer.saveCacheToFile(unloadedStorage, unloadedEntitiesCacheFile, userPreProcessor = true)
      }
      else {
        Files.deleteIfExists(unloadedEntitiesCacheFile)
      }
    }
    else {
      Files.deleteIfExists(cacheFile)
      Files.deleteIfExists(unloadedEntitiesCacheFile)
    }
  }

  @TestOnly
  fun getUnloadedEntitiesCacheFilePath(): Path = unloadedEntitiesCacheFile

  override fun loadCache(): EntityStorage? {
    return cacheSerializer.loadCacheFromFile(cacheFile, invalidateCachesMarkerFile, invalidateProjectCacheMarkerFile)
  }
  override fun loadUnloadedEntitiesCache(): EntityStorage? {
    return cacheSerializer.loadCacheFromFile(unloadedEntitiesCacheFile, invalidateCachesMarkerFile,
                                             invalidateProjectCacheMarkerFile)
  }
  override fun dispose() = Unit

  private fun invalidateProjectCache() {
    LOG.info("Invalidating project model cache by creating $invalidateProjectCacheMarkerFile")
    invalidateCaches(cachesInvalidated, invalidateProjectCacheMarkerFile)
  }

  companion object {
    private val LOG = logger<WorkspaceModelCacheImpl>()
    internal const val DATA_DIR_NAME = "project-model-cache"
    private var forceEnableCaching = false

    @TestOnly
    var testCacheFile: Path? = null

    private val cachesInvalidated = AtomicBoolean(false)
    internal val invalidateCachesMarkerFile = projectsDataDir.resolve(".invalidate")

    fun invalidateCaches() {
      LOG.info("Invalidating caches by creating $invalidateCachesMarkerFile")
      invalidateCaches(cachesInvalidated, invalidateCachesMarkerFile)
    }

    fun forceEnableCaching(disposable: Disposable) {
      forceEnableCaching = true
      Disposer.register(disposable) { forceEnableCaching = false }
    }
  }
}
