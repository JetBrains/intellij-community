// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.cl.PluginAwareClassLoader
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.PluginId
import com.intellij.platform.backend.workspace.WorkspaceModelCacheVersion
import com.intellij.platform.diagnostic.telemetry.helpers.addMeasuredTimeMillis
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.impl.serialization.EntityStorageSerializerImpl
import com.intellij.platform.workspace.storage.url.UrlRelativizer
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager
import com.intellij.util.io.basicAttributesIfExists
import com.intellij.util.io.write
import com.intellij.workspaceModel.ide.NonPersistentEntitySource
import io.opentelemetry.api.metrics.Meter
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.io.path.exists
import kotlin.io.path.getLastModifiedTime

class WorkspaceModelCacheSerializer(vfuManager: VirtualFileUrlManager, urlRelativizer: UrlRelativizer?) {
  private val serializer: EntityStorageSerializer =
    EntityStorageSerializerImpl(
      PluginAwareEntityTypesResolver,
      vfuManager,
      urlRelativizer
    )

  internal fun loadCacheFromFile(file: Path,
                                 invalidateGlobalCachesMarkerFile: Path,
                                 invalidateCachesMarkerFile: Path): MutableEntityStorage? = loadCacheFromFileTimeMs.addMeasuredTimeMillis {
    val start = System.currentTimeMillis()
    val cacheFileAttributes = file.basicAttributesIfExists() ?: return@addMeasuredTimeMillis null

    val invalidateCachesMarkerFileAttributes = invalidateGlobalCachesMarkerFile.basicAttributesIfExists()
    if ((invalidateCachesMarkerFileAttributes != null && cacheFileAttributes.lastModifiedTime() < invalidateCachesMarkerFileAttributes.lastModifiedTime()) ||
        invalidateCachesMarkerFile.exists() && cacheFileAttributes.lastModifiedTime() < invalidateCachesMarkerFile.getLastModifiedTime()) {
      LOG.info("Skipping cache loading since '${invalidateGlobalCachesMarkerFile}' is present and newer than cache file '$file'")
      runCatching { Files.deleteIfExists(file) }
      return@addMeasuredTimeMillis null
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

    return@addMeasuredTimeMillis cache
  }

  // Serialize and atomically replace cacheFile. Delete temporary file in any cache to avoid junk in cache folder
  internal fun saveCacheToFile(storage: ImmutableEntityStorage,
                               file: Path,
                               userPreProcessor: Boolean = false): SaveInfo = saveCacheToFileTimeMs.addMeasuredTimeMillis {
    val start = System.currentTimeMillis()

    LOG.debug("Saving Workspace model cache to $file")
    val dir = file.parent
    Files.createDirectories(dir)
    var cacheSize: Long? = null
    val tmpFile = Files.createTempFile(dir, "cache", ".tmp")
    try {
      val serializationResult = serializer.serializeCache(tmpFile, if (userPreProcessor) cachePreProcess(storage) else storage)
      when (serializationResult) {
        is SerializationResult.Fail -> LOG.warn("Workspace model cache was not serialized", serializationResult.problem)
        is SerializationResult.Success -> cacheSize = serializationResult.size
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

    return@addMeasuredTimeMillis SaveInfo(System.currentTimeMillis() - start, cacheSize)
  }

  // Looks like https://opentelemetry.io/docs/specs/otel/metrics/api/#histogram
  data class SaveInfo(
    val loadingTime: Long,
    val loadedSize: Long?,
  )

  private fun cachePreProcess(storage: ImmutableEntityStorage): ImmutableEntityStorage {
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
      val classLoader = getClassLoader(pluginId) ?:
        error("Could not resolve class loader for plugin '$pluginId' with type: $name")

      if (name.startsWith("[")) return Class.forName(name, true, classLoader)
      return classLoader.loadClass(name)
    }

    override fun getClassLoader(pluginId: String?): ClassLoader? {
      val id = pluginId?.let { PluginId.getId(it) }
      if (id != null && !PluginManagerCore.isPluginInstalled(id)) {
         return null
      }

      val plugin = PluginManagerCore.getPlugin(id)
      return plugin?.pluginClassLoader ?: ApplicationManager::class.java.classLoader
    }

  }

  companion object {
    private val WORKSPACE_MODEL_CACHE_VERSION_EP = ExtensionPointName<WorkspaceModelCacheVersion>("com.intellij.workspaceModel.cache.version")

    fun collectExternalCacheVersions(): Map<String, String> {
      return WORKSPACE_MODEL_CACHE_VERSION_EP.extensionList.associate { it.getId() to it.getVersion() }
    }

    private val loadCacheFromFileTimeMs: AtomicLong = AtomicLong()
    private val saveCacheToFileTimeMs: AtomicLong = AtomicLong()

    private fun setupOpenTelemetryReporting(meter: Meter) {
      val loadCacheFromFileTimeCounter = meter.counterBuilder("workspaceModel.load.cache.from.file.ms").buildObserver()
      val saveCacheToFileTimeCounter = meter.counterBuilder("workspaceModel.save.cache.to.file.ms").buildObserver()

      meter.batchCallback(
        {
          loadCacheFromFileTimeCounter.record(loadCacheFromFileTimeMs.get())
          saveCacheToFileTimeCounter.record(saveCacheToFileTimeMs.get())
        },
        loadCacheFromFileTimeCounter, saveCacheToFileTimeCounter
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
