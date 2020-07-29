// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.ide.impl

import com.google.common.base.Stopwatch
import com.google.common.hash.Hashing
import com.intellij.ide.plugins.PluginManager
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.appSystemDir
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.pooledThreadSingleAlarm
import com.intellij.workspaceModel.storage.impl.EntityStorageSerializerImpl
import com.intellij.workspaceModel.ide.WorkspaceModel
import com.intellij.workspaceModel.ide.WorkspaceModelChangeListener
import com.intellij.workspaceModel.ide.WorkspaceModelTopics
import com.intellij.workspaceModel.ide.getInstance
import com.intellij.workspaceModel.storage.*
import org.jetbrains.annotations.ApiStatus
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.atomic.AtomicBoolean

@ApiStatus.Internal
internal class WorkspaceModelCacheImpl(private val project: Project, parentDisposable: Disposable): Disposable {
  private val LOG = Logger.getInstance(javaClass)

  private val cacheFile: File
  private val virtualFileManager: VirtualFileUrlManager = VirtualFileUrlManager.getInstance(project)
  private val serializer: EntityStorageSerializer = EntityStorageSerializerImpl(PluginAwareEntityTypesResolver, virtualFileManager)

  init {
    Disposer.register(parentDisposable, this)

    val hasher = Hashing.sha256().newHasher()
    project.basePath?.let { hasher.putString(it, Charsets.UTF_8) }
    project.projectFilePath?.let { hasher.putString(it, Charsets.UTF_8) }
    hasher.putString(project.locationHash, Charsets.UTF_8)
    hasher.putString(serializer.javaClass.name, Charsets.UTF_8)
    hasher.putString(serializer.serializerDataFormatVersion, Charsets.UTF_8)

    cacheFile = File(cacheDir, hasher.hash().toString().substring(0, 20) + ".data")

    LOG.info("Project Model Cache at $cacheFile")

    WorkspaceModelTopics.getInstance(project).subscribeImmediately(project.messageBus.connect(this), object : WorkspaceModelChangeListener {
      override fun changed(event: VersionedStorageChanged) = LOG.bracket("${javaClass.simpleName}.EntityStoreChange") {
        saveAlarm.request()
      }
    })
  }

  private val saveAlarm = pooledThreadSingleAlarm(1000, this) {
    val storage = WorkspaceModel.getInstance(project).entityStorage.current

    if (!cachesInvalidated.get()) {
      LOG.info("Saving project model cache to $cacheFile")
      saveCache(storage)
    }

    if (cachesInvalidated.get()) {
      FileUtil.delete(cacheFile)
    }
  }

  override fun dispose() = Unit

  fun loadCache(): WorkspaceEntityStorage? {
    try {
      if (!cacheFile.exists()) return null

      if (invalidateCachesMarkerFile.exists() && cacheFile.lastModified() < invalidateCachesMarkerFile.lastModified()) {
        LOG.info("Skipping project model cache since '$invalidateCachesMarkerFile' is present and newer than cache file '$cacheFile'")
        FileUtil.delete(cacheFile)
        return null
      }

      LOG.info("Loading project model cache from $cacheFile")

      val stopWatch = Stopwatch.createStarted()
      val builder = cacheFile.inputStream().use { serializer.deserializeCache(it) }
      LOG.info("Loaded project model cache from $cacheFile in ${stopWatch.stop()}")

      return builder
    } catch (t: Throwable) {
      LOG.warn("Could not deserialize project model cache from $cacheFile", t)
      return null
    }
  }

  // Serialize and atomically replace cacheFile. Delete temporary file in any cache to avoid junk in cache folder
  private fun saveCache(storage: WorkspaceEntityStorage) {
    val tmpFile = FileUtil.createTempFile(cacheFile.parentFile, "cache", ".tmp")
    try {
      tmpFile.outputStream().use { serializer.serializeCache(it, storage) }

      try {
        Files.move(tmpFile.toPath(), cacheFile.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
      }
      catch (e: AtomicMoveNotSupportedException) {
        LOG.warn(e)
        Files.move(tmpFile.toPath(), cacheFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
      }
    } finally {
      tmpFile.delete()
    }
  }

  private object PluginAwareEntityTypesResolver: EntityTypesResolver {
    override fun getPluginId(clazz: Class<*>): String? = PluginManager.getInstance().getPluginOrPlatformByClassName(clazz.name)?.idString

    override fun resolveClass(name: String, pluginId: String?): Class<*> {
      val id = pluginId?.let { PluginId.getId(it) }
      val classloader = if (id == null) {
        ApplicationManager::class.java.classLoader
      } else {
        val plugin = PluginManagerCore.getPlugin(id) ?: error("Could not resolve plugin by id '$pluginId' for type: $name")
        plugin.pluginClassLoader ?: ApplicationManager::class.java.classLoader
      }

      return classloader.loadClass(name)
    }
  }

  companion object {
    private val LOG = logger<WorkspaceModelCacheImpl>()

    private val cacheDir = appSystemDir.resolve("projectModelCache").toFile()

    private val cachesInvalidated = AtomicBoolean(false)
    private val invalidateCachesMarkerFile = File(cacheDir, ".invalidate")

    fun invalidateCaches() {
      LOG.info("Invalidating project model caches by creating $invalidateCachesMarkerFile")

      cachesInvalidated.set(true)

      try {
        FileUtil.createDirectory(cacheDir)
        FileUtil.writeToFile(invalidateCachesMarkerFile, System.currentTimeMillis().toString())
      }
      catch (t: Throwable) {
        LOG.warn("Cannot update the invalidation marker file", t)
      }

      ApplicationManager.getApplication().executeOnPooledThread {
        val filesToRemove = (cacheDir.listFiles() ?: emptyArray()).filter { it.isFile && !it.name.startsWith(".") }
        FileUtil.asyncDelete(filesToRemove)
      }
    }
  }
}
