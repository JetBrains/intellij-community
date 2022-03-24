// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl

import com.google.common.base.Stopwatch
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.cl.PluginAwareClassLoader
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.getProjectDataPath
import com.intellij.openapi.project.projectsDataDir
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.SingleAlarm
import com.intellij.util.SystemProperties
import com.intellij.util.io.exists
import com.intellij.util.io.inputStream
import com.intellij.util.io.lastModified
import com.intellij.workspaceModel.ide.*
import com.intellij.workspaceModel.storage.*
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleEntity
import com.intellij.workspaceModel.storage.impl.EntityStorageSerializerImpl
import com.intellij.workspaceModel.storage.impl.isConsistent
import com.intellij.workspaceModel.storage.url.VirtualFileUrlManager
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.atomic.AtomicBoolean
import java.util.stream.Collectors

@ApiStatus.Internal
class WorkspaceModelCacheImpl(private val project: Project) : Disposable, WorkspaceModelCache {
  override val enabled = forceEnableCaching || !ApplicationManager.getApplication().isUnitTestMode

  private val cacheFile by lazy { initCacheFile() }
  private val invalidateProjectCacheMarkerFile by lazy { project.getProjectDataPath(DATA_DIR_NAME).resolve(".invalidate").toFile() }
  private val virtualFileManager: VirtualFileUrlManager = VirtualFileUrlManager.getInstance(project)
  private val serializer: EntityStorageSerializer = EntityStorageSerializerImpl(PluginAwareEntityTypesResolver, virtualFileManager,
                                                                                WorkspaceModelCacheImpl::collectExternalCacheVersions)

  init {
    if (enabled) {
      LOG.debug("Project Model Cache at $cacheFile")

      WorkspaceModelTopics.getInstance(project).subscribeImmediately(project.messageBus.connect(this), object : WorkspaceModelChangeListener {
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
      val testFile = testCacheFile!!.toPath()
      if (!testFile.exists()) {
        error("Test cache file defined, but doesn't exist")
      }
      return testFile
    }

    return project.getProjectDataPath(DATA_DIR_NAME).resolve("cache.data")
  }

  private val saveAlarm = SingleAlarm.pooledThreadSingleAlarm(1000, this) { this.doCacheSaving() }

  override fun saveCacheNow() {
    saveAlarm.cancel()
    doCacheSaving()
  }

  private fun doCacheSaving() {
    val storage = WorkspaceModel.getInstance(project).entityStorage.current
    if (!storage.isConsistent) {
      invalidateProjectCache()
    }

    if (!cachesInvalidated.get()) {
      LOG.debug("Saving project model cache to $cacheFile")
      val processedStorage = cachePreProcess(storage)
      saveCache(processedStorage)
    }

    if (cachesInvalidated.get()) {
      FileUtil.delete(cacheFile)
    }
  }

  private fun cachePreProcess(storage: WorkspaceEntityStorage): WorkspaceEntityStorage {
    val builder = WorkspaceEntityStorageBuilder.from(storage)
    val nonPersistentModules = builder.entities(ModuleEntity::class.java)
      .filter { it.entitySource == NonPersistentEntitySource }
      .toList()
    nonPersistentModules.forEach {
      builder.removeEntity(it)
    }
    return builder.toStorage()
  }

  override fun dispose() = Unit

  override fun loadCache(): WorkspaceEntityStorage? {
    try {
      if (!cacheFile.exists()) return null

      if (invalidateCachesMarkerFile.exists() && cacheFile.lastModified().toMillis() < invalidateCachesMarkerFile.lastModified() ||
          invalidateProjectCacheMarkerFile.exists() && cacheFile.lastModified().toMillis() < invalidateProjectCacheMarkerFile.lastModified()) {
        LOG.info("Skipping project model cache since '$invalidateCachesMarkerFile' is present and newer than cache file '$cacheFile'")
        FileUtil.delete(cacheFile)
        return null
      }

      LOG.debug("Loading project model cache from $cacheFile")

      val stopWatch = Stopwatch.createStarted()
      val builder = cacheFile.inputStream().use { serializer.deserializeCache(it) }
      LOG.debug("Loaded project model cache from $cacheFile in ${stopWatch.stop()}")

      return builder
    }
    catch (t: Throwable) {
      LOG.warn("Could not deserialize project model cache from $cacheFile", t)
      return null
    }
  }

  // Serialize and atomically replace cacheFile. Delete temporary file in any cache to avoid junk in cache folder
  private fun saveCache(storage: WorkspaceEntityStorage) {
    val tmpFile = FileUtil.createTempFile(cacheFile.parent.toFile(), "cache", ".tmp")
    try {
      val serializationResult = tmpFile.outputStream().use { serializer.serializeCache(it, storage) }
      if (serializationResult is SerializationResult.Fail<*>) {
        LOG.warn("Workspace model cache was not serialized: ${serializationResult.info}")
      }

      try {
        Files.move(tmpFile.toPath(), cacheFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
      }
      catch (e: AtomicMoveNotSupportedException) {
        LOG.warn(e)
        Files.move(tmpFile.toPath(), cacheFile, StandardCopyOption.REPLACE_EXISTING)
      }
    }
    finally {
      tmpFile.delete()
    }
  }

  private fun invalidateProjectCache() {
    LOG.info("Invalidating project model cache by creating $invalidateProjectCacheMarkerFile")

    cachesInvalidated.set(true)

    try {
      FileUtil.createParentDirs(invalidateProjectCacheMarkerFile)
      FileUtil.writeToFile(invalidateProjectCacheMarkerFile, System.currentTimeMillis().toString())
    }
    catch (t: Throwable) {
      LOG.warn("Cannot update the project invalidation marker file", t)
    }
  }

  object PluginAwareEntityTypesResolver : EntityTypesResolver {
    override fun getPluginId(clazz: Class<*>): String? {
      return (clazz.classLoader as? PluginAwareClassLoader)?.pluginDescriptor?.pluginId?.idString
    }

    override fun resolveClass(name: String, pluginId: String?): Class<*> {
      val id = pluginId?.let { PluginId.getId(it) }
      val classloader = if (id == null) {
        ApplicationManager::class.java.classLoader
      }
      else {
        val plugin = PluginManagerCore.getPlugin(id) ?: error("Could not resolve plugin by id '$pluginId' for type: $name")
        plugin.pluginClassLoader ?: ApplicationManager::class.java.classLoader
      }

      if (name.startsWith("[")) return Class.forName(name, true, classloader)
      return classloader.loadClass(name)
    }
  }

  companion object {
    private val LOG = logger<WorkspaceModelCacheImpl>()
    internal const val DATA_DIR_NAME = "project-model-cache"
    private var forceEnableCaching = false

    @TestOnly
    var testCacheFile: File? = null

    private val cachesInvalidated = AtomicBoolean(false)
    internal val invalidateCachesMarkerFile = File(projectsDataDir.toFile(), ".invalidate")

    fun invalidateCaches() {
      LOG.info("Invalidating caches by creating $invalidateCachesMarkerFile")

      cachesInvalidated.set(true)

      try {
        FileUtil.createParentDirs(invalidateCachesMarkerFile)
        FileUtil.writeToFile(invalidateCachesMarkerFile, System.currentTimeMillis().toString())
      }
      catch (t: Throwable) {
        LOG.warn("Cannot update the invalidation marker file", t)
      }
    }

    private val WORKSPACE_MODEL_CACHE_VERSION_EP = ExtensionPointName.create<WorkspaceModelCacheVersion>("com.intellij.workspaceModel.cache.version")

    fun collectExternalCacheVersions(): Map<String, String> {
      return WORKSPACE_MODEL_CACHE_VERSION_EP
        .extensions()
        .collect(Collectors.toMap(WorkspaceModelCacheVersion::getId, WorkspaceModelCacheVersion::getVersion))
    }

    fun forceEnableCaching(disposable: Disposable) {
      forceEnableCaching = true
      Disposer.register(disposable) { forceEnableCaching = false }
    }
  }
}
