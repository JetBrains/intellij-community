// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.cl.PluginAwareClassLoader
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.PluginId
import com.intellij.platform.diagnostic.telemetry.helpers.addElapsedTimeMs
import com.intellij.util.io.basicAttributesIfExists
import com.intellij.util.io.lastModified
import com.intellij.util.io.write
import com.intellij.workspaceModel.ide.NonPersistentEntitySource
import com.intellij.workspaceModel.ide.WorkspaceModelCacheVersion
import com.intellij.workspaceModel.storage.*
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleEntity
import com.intellij.workspaceModel.storage.impl.EntityStorageSerializerImpl
import com.intellij.workspaceModel.storage.url.VirtualFileUrlManager
import io.opentelemetry.api.metrics.Meter
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.io.path.exists

class WorkspaceModelCacheSerializer(vfuManager: VirtualFileUrlManager) {
  private val serializer: EntityStorageSerializer = EntityStorageSerializerImpl(PluginAwareEntityTypesResolver, vfuManager,
                                                                                ::collectExternalCacheVersions)

  internal fun loadCacheFromFile(file: Path, invalidateGlobalCachesMarkerFile: Path, invalidateCachesMarkerFile: Path): EntityStorage? {
    val start = System.currentTimeMillis()
    val cacheFileAttributes = file.basicAttributesIfExists() ?: return null

    val invalidateCachesMarkerFileAttributes = invalidateGlobalCachesMarkerFile.basicAttributesIfExists()
    if ((invalidateCachesMarkerFileAttributes != null && cacheFileAttributes.lastModifiedTime() < invalidateCachesMarkerFileAttributes.lastModifiedTime()) ||
        invalidateCachesMarkerFile.exists() && cacheFileAttributes.lastModifiedTime() < invalidateCachesMarkerFile.lastModified()) {
      LOG.info("Skipping cache loading since '${invalidateGlobalCachesMarkerFile}' is present and newer than cache file '$file'")
      runCatching { Files.deleteIfExists(file) }
      return null
    }

    LOG.debug("Loading cache from $file")

    val cache = serializer.deserializeCache(file)
      .onSuccess {
        if (it != null) {
          LOG.debug("Loaded cache from $file in ${System.currentTimeMillis() - start}ms")
        }
      }
      .onFailure {
        LOG.warn("Could not deserialize cache from $file", it)
      }
      .getOrNull()

    loadCacheFromFileTimeMs.addElapsedTimeMs(start)
    return cache
  }

  // Serialize and atomically replace cacheFile. Delete temporary file in any cache to avoid junk in cache folder
  internal fun saveCacheToFile(storage: EntityStorageSnapshot, file: Path, userPreProcessor: Boolean = false) {
    val start = System.currentTimeMillis()

    LOG.debug("Saving Workspace model cache to $file")
    val dir = file.parent
    Files.createDirectories(dir)
    val tmpFile = Files.createTempFile(dir, "cache", ".tmp")
    try {
      val serializationResult = serializer.serializeCache(tmpFile, if (userPreProcessor) cachePreProcess(storage) else storage)
      if (serializationResult is SerializationResult.Fail<*>) {
        LOG.warn("Workspace model cache was not serialized: ${serializationResult.info}")
      }

      try {
        Files.move(tmpFile, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
      }
      catch (e: AtomicMoveNotSupportedException) {
        LOG.warn(e)
        Files.move(tmpFile, file, StandardCopyOption.REPLACE_EXISTING)
      }
    }
    finally {
      Files.deleteIfExists(tmpFile)
    }
    saveCacheToFileTimeMs.addElapsedTimeMs(start)
  }

  private fun cachePreProcess(storage: EntityStorage): EntityStorageSnapshot {
    val builder = MutableEntityStorage.from(storage)
    val nonPersistentModules = builder.entities(ModuleEntity::class.java)
      .filter { it.entitySource == NonPersistentEntitySource }
      .toList()
    nonPersistentModules.forEach {
      builder.removeEntity(it)
    }
    return builder.toSnapshot()
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
    private val WORKSPACE_MODEL_CACHE_VERSION_EP = ExtensionPointName<WorkspaceModelCacheVersion>("com.intellij.workspaceModel.cache.version")

    fun collectExternalCacheVersions(): Map<String, String> {
      return WORKSPACE_MODEL_CACHE_VERSION_EP.extensionList.associate { it.getId() to it.getVersion() }
    }

    private val loadCacheFromFileTimeMs: AtomicLong = AtomicLong()
    private val saveCacheToFileTimeMs: AtomicLong = AtomicLong()

    private fun setupOpenTelemetryReporting(meter: Meter): Unit {
      val loadCacheFromFileTimeGauge = meter.gaugeBuilder("workspaceModel.load.cache.from.file.ms")
        .ofLongs().setDescription("Total time spent in method").buildObserver()

      val saveCacheToFileTimeGauge = meter.gaugeBuilder("workspaceModel.save.cache.to.file.ms")
        .ofLongs().setDescription("Total time spent in method").buildObserver()

      meter.batchCallback(
        {
          loadCacheFromFileTimeGauge.record(loadCacheFromFileTimeMs.get())
          saveCacheToFileTimeGauge.record(saveCacheToFileTimeMs.get())
        },
        loadCacheFromFileTimeGauge, saveCacheToFileTimeGauge
      )
    }

    init {
      setupOpenTelemetryReporting(jpsMetrics.meter)
    }
  }
}

private val LOG = logger<WorkspaceModelCacheSerializer>()

internal fun invalidateCaches(cachesInvalidatedMarkerBoolean: AtomicBoolean, invalidateCachesMarkerFile: Path) {
  cachesInvalidatedMarkerBoolean.set(true)
  try {
    invalidateCachesMarkerFile.write(System.currentTimeMillis().toString())
  }
  catch (t: Throwable) {
    LOG.warn("Cannot update the invalidation marker file", t)
  }
}