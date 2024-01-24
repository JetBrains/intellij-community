// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.getProjectDataPath
import com.intellij.openapi.project.projectsDataDir
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.backend.workspace.WorkspaceModelCache
import com.intellij.platform.backend.workspace.WorkspaceModelChangeListener
import com.intellij.platform.backend.workspace.WorkspaceModelTopics
import com.intellij.platform.backend.workspace.impl.internal
import com.intellij.platform.diagnostic.telemetry.helpers.addMeasuredTimeMillis
import com.intellij.platform.workspace.storage.ImmutableEntityStorage
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.VersionedStorageChange
import com.intellij.platform.workspace.storage.impl.assertConsistency
import com.intellij.platform.workspace.storage.impl.isConsistent
import io.opentelemetry.api.metrics.Meter
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.io.path.exists
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.measureTime
import kotlin.time.measureTimedValue

@OptIn(FlowPreview::class)
@ApiStatus.Internal
class WorkspaceModelCacheImpl(private val project: Project, coroutineScope: CoroutineScope) : WorkspaceModelCache {
  override val enabled: Boolean = forceEnableCaching || !ApplicationManager.getApplication().isUnitTestMode
  private val saveRequests = MutableSharedFlow<Unit>(replay=1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  private val cacheFile by lazy { initCacheFile() }
  private val unloadedEntitiesCacheFile by lazy { project.getProjectDataPath(DATA_DIR_NAME).resolve("unloaded-entities-cache.data") }
  private val invalidateProjectCacheMarkerFile by lazy { project.getProjectDataPath(DATA_DIR_NAME).resolve(".invalidate") }

  private val urlRelativizer =
    if (Registry.`is`("ide.workspace.model.store.relative.paths.in.cache", true)) {
      JpsProjectUrlRelativizer(project)
    } else {
      null
    }

  private val cacheSerializer = WorkspaceModelCacheSerializer(WorkspaceModel.getInstance(project).getVirtualFileUrlManager(),
                                                              urlRelativizer)

  init {
    if (enabled) {
      LOG.debug("Project Model Cache at $cacheFile")

      project.messageBus.connect(coroutineScope).subscribe(WorkspaceModelTopics.CHANGED, object : WorkspaceModelChangeListener {
        override fun changed(event: VersionedStorageChange) {
          LOG.debug("Schedule cache update")
          check(saveRequests.tryEmit(Unit))
        }
      })

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

  @TestOnly
  override fun saveCacheNow() {
    doCacheSaving()
  }

  private fun doCacheSaving(): Unit = saveWorkspaceModelCachesTimeMs.addMeasuredTimeMillis {
    val storage = WorkspaceModel.getInstance(project).currentSnapshot
    val unloadedStorage = WorkspaceModel.getInstance(project).internal.currentSnapshotOfUnloadedEntities
    if (!storage.isConsistent || !unloadedStorage.isConsistent) {
      invalidateProjectCache()
    }

    if (!cachesInvalidated.get()) {
      LOG.debug("Saving project model cache to $cacheFile")

      // Make sure we don't save the cache that is broken
      val assertConsistencyDuration = measureTime { storage.assertConsistency() }

      val (timeMs, size) = cacheSerializer.saveCacheToFile(storage, cacheFile, userPreProcessor = true)
      WorkspaceModelFusLogger.logCacheSave(timeMs + assertConsistencyDuration.inWholeMilliseconds, size ?: -1)
      //todo check that where are no entities in the storage instead
      if (unloadedStorage != ImmutableEntityStorage.empty()) {
        LOG.debug("Saving project model cache to $unloadedEntitiesCacheFile")
        unloadedStorage.assertConsistency()
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

  override fun loadCache(): MutableEntityStorage? {
    val (cache, time) = measureTimedValue {
      cacheSerializer.loadCacheFromFile(cacheFile, invalidateCachesMarkerFile, invalidateProjectCacheMarkerFile)
    }
    WorkspaceModelFusLogger.logCacheLoading(if (cache != null) time.inWholeMilliseconds else -1)
    return cache
  }
  override fun loadUnloadedEntitiesCache(): MutableEntityStorage? {
    return cacheSerializer.loadCacheFromFile(unloadedEntitiesCacheFile, invalidateCachesMarkerFile,
                                             invalidateProjectCacheMarkerFile)
  }

  private fun invalidateProjectCache() {
    LOG.info("Invalidating project model cache by creating $invalidateProjectCacheMarkerFile")
    invalidateCaches(cachesInvalidated, invalidateProjectCacheMarkerFile)
  }

  companion object {
    private val LOG = logger<WorkspaceModelCacheImpl>()
    internal const val DATA_DIR_NAME: String = "project-model-cache"
    private var forceEnableCaching = false

    @TestOnly
    var testCacheFile: Path? = null

    private val cachesInvalidated = AtomicBoolean(false)
    internal val invalidateCachesMarkerFile: Path = projectsDataDir.resolve(".invalidate")

    fun invalidateCaches() {
      LOG.info("Invalidating caches by creating $invalidateCachesMarkerFile")
      invalidateCaches(cachesInvalidated, invalidateCachesMarkerFile)
    }

    fun forceEnableCaching(disposable: Disposable) {
      forceEnableCaching = true
      Disposer.register(disposable) { forceEnableCaching = false }
    }

    private val saveWorkspaceModelCachesTimeMs: AtomicLong = AtomicLong()

    private fun setupOpenTelemetryReporting(meter: Meter) {
      val saveWorkspaceModelCachesTimeCounter = meter.counterBuilder("workspaceModel.do.save.caches.ms").buildObserver()

      meter.batchCallback(
        {
          saveWorkspaceModelCachesTimeCounter.record(saveWorkspaceModelCachesTimeMs.get())
        },
        saveWorkspaceModelCachesTimeCounter
      )
    }

    init {
      setupOpenTelemetryReporting(jpsMetrics.meter)
    }
  }
}
