// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.cl.PluginAwareClassLoader
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.io.*
import com.intellij.workspaceModel.ide.NonPersistentEntitySource
import com.intellij.workspaceModel.ide.getGlobalInstance
import com.intellij.workspaceModel.storage.*
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleEntity
import com.intellij.workspaceModel.storage.impl.EntityStorageSerializerImpl
import com.intellij.workspaceModel.storage.url.VirtualFileUrlManager
import org.jetbrains.annotations.ApiStatus
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.atomic.AtomicBoolean

@ApiStatus.Internal
abstract class AbstractWorkspaceModelCache(cacheVersionsContributor: () -> Map<String, String> = { emptyMap() }) {
  private val serializer: EntityStorageSerializer = EntityStorageSerializerImpl(PluginAwareEntityTypesResolver,
                                                                                VirtualFileUrlManager.getGlobalInstance(),
                                                                                cacheVersionsContributor)

  internal fun loadCache(file: Path, invalidateGlobalCachesMarkerFile: Path, invalidateCachesMarkerFile: Path): EntityStorage? {
    val cacheFileAttributes = file.basicAttributesIfExists() ?: return null

    val invalidateCachesMarkerFileAttributes = invalidateGlobalCachesMarkerFile.basicAttributesIfExists()
    if ((invalidateCachesMarkerFileAttributes != null && cacheFileAttributes.lastModifiedTime() < invalidateCachesMarkerFileAttributes.lastModifiedTime()) ||
        invalidateCachesMarkerFile.exists() && cacheFileAttributes.lastModifiedTime() < invalidateCachesMarkerFile.lastModified()) {
      LOG.info("Skipping cache loading since '${invalidateGlobalCachesMarkerFile}' is present and newer than cache file '$file'")
      runCatching { Files.deleteIfExists(file) }
      return null
    }

    LOG.debug("Loading cache from $file")

    val start = System.currentTimeMillis()
    val deserializationResult = file.inputStream().use { serializer.deserializeCache(it) }
    if (LOG.isDebugEnabled) {
      LOG.debug("Loaded cache from $file in ${System.currentTimeMillis() - start}ms")
    }

    return deserializationResult
      .onSuccess {
        when {
          it != null -> LOG.debug("Loaded cache from $file in ${System.currentTimeMillis() - start}ms")
          else -> LOG.debug("Cannot load cache from $file in ${System.currentTimeMillis() - start}ms")
        }
      }
      .onFailure {
        LOG.warn("Could not deserialize cache from $file", it)
      }
      .getOrNull()
  }

  // Serialize and atomically replace cacheFile. Delete temporary file in any cache to avoid junk in cache folder
  internal fun saveCache(storage: EntityStorageSnapshot, file: Path) {
    LOG.debug("Saving project model cache to $file")
    val tmpFile = FileUtil.createTempFile(file.parent.toFile(), "cache", ".tmp")
    try {
      val serializationResult = tmpFile.outputStream().use { serializer.serializeCache(it, cachePreProcess(storage)) }
      if (serializationResult is SerializationResult.Fail<*>) {
        LOG.warn("Workspace model cache was not serialized: ${serializationResult.info}")
      }

      try {
        Files.move(tmpFile.toPath(), file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
      }
      catch (e: AtomicMoveNotSupportedException) {
        LOG.warn(e)
        Files.move(tmpFile.toPath(), file, StandardCopyOption.REPLACE_EXISTING)
      }
    }
    finally {
      tmpFile.delete()
    }
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
}

private val LOG = logger<AbstractWorkspaceModelCache>()

internal fun invalidateCaches(cachesInvalidatedMarkerBoolean: AtomicBoolean, invalidateCachesMarkerFile: Path) {
  cachesInvalidatedMarkerBoolean.set(true)
  try {
    invalidateCachesMarkerFile.write(System.currentTimeMillis().toString())
  }
  catch (t: Throwable) {
    LOG.warn("Cannot update the invalidation marker file", t)
  }
}