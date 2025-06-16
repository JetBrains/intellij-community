// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.impl.eel

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.io.toNioPathOrNull
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.local.FileWatcherNotificationSink
import com.intellij.openapi.vfs.local.PluggableFileWatcher
import com.intellij.openapi.vfs.newvfs.ManagingFS
import com.intellij.platform.core.nio.fs.MultiRoutingFileSystemProvider
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.fs.EelFileSystemApi.FileChangeType
import com.intellij.platform.eel.fs.EelFileSystemApi.PathChange
import com.intellij.platform.eel.fs.WatchOptionsBuilder
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.platform.eel.provider.asEelPath
import com.intellij.platform.eel.provider.upgradeBlocking
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.nio.file.FileSystems
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.Volatile

class EelFileWatcher : PluggableFileWatcher() {

  private lateinit var myNotificationSink: FileWatcherNotificationSink
  private val watchedDevcontainers = ConcurrentHashMap<String, WatchedDevcontainer>()
  private val mySettingRoots = AtomicInteger(0)
  private val watchedOptions = setOf(FileChangeType.CHANGED, FileChangeType.CREATED, FileChangeType.DELETED)

  @Volatile
  private var myShuttingDown = false

  override fun initialize(managingFS: ManagingFS, notificationSink: FileWatcherNotificationSink) {
    if (!useEelFileWatcher()) return
    myNotificationSink = notificationSink
  }

  override fun dispose() {
    myShuttingDown = true
    shutdownWatcherJobs()
  }

  override fun isOperational(): Boolean = useEelFileWatcher()

  override fun isSettingRoots(): Boolean = isOperational() && mySettingRoots.get() > 0

  override fun setWatchRoots(recursive: List<String>, flat: List<String>, shuttingDown: Boolean) {
    val recursiveFiltered = recursive.mapNotNull { it.getDevcontainerPathInfo() }
    val flatFiltered = flat.mapNotNull { it.getDevcontainerPathInfo() }
    if (recursiveFiltered.isEmpty() && flatFiltered.isEmpty()) return
    if (shuttingDown) {
      myShuttingDown = true
      shutdownWatcherJobs()
      return
    }

    val newData = HashMap<String, DevcontainerData>()
    sortRoots(recursiveFiltered, newData, true)
    sortRoots(flatFiltered, newData, false)

    newData.forEach { (key, incoming) ->
      val existing = watchedDevcontainers[key]
      when {
        existing == null -> watchedDevcontainers[key] = WatchedDevcontainer(incoming, setupWatcherJob(incoming))
        existing.data != incoming -> {
          existing.cancel()
          existing.data.reload(incoming)
          watchedDevcontainers[key] = WatchedDevcontainer(incoming, setupWatcherJob(incoming))
        }
        else -> myNotificationSink.notifyManualWatchRoots(this, existing.data.ignored)
      }
    }

    watchedDevcontainers.entries.removeIf { (key, value) ->
      if (!newData.containsKey(key)) {
        value.cancel()
        true
      } else false
    }
  }

  private fun sortRoots(roots: List<DevcontainerPathInfo>,
                        devcontainerData: MutableMap<String, DevcontainerData>,
                        recursive: Boolean) {
    roots.forEach { info ->
      val prefix = info.descriptor.toString()
      val data: DevcontainerData = devcontainerData.computeIfAbsent(prefix) { DevcontainerData(prefix, info.descriptor) }
      (if (recursive) data.recursive else data.flat)[info.path] = info.absolutePath
    }
  }

  @OptIn(DelicateCoroutinesApi::class)
  private fun setupWatcherJob(data: DevcontainerData): () -> Unit {
    if (myShuttingDown) return {}

    mySettingRoots.incrementAndGet()
    val scope = GlobalScope.childScope("IJentFileWatcher")
    val eel = data.descriptor.upgradeBlocking()

    val job = scope.launch {
      val flow = eel.fs.watchChanges(
        WatchOptionsBuilder().changeTypes(watchedOptions).paths(data.getWatchedPaths()).build())
      val job = scope.launch { flow.collect { notifyChange(it, data) } }
      mySettingRoots.decrementAndGet()
      job
    }

    return {
      job.cancel()
      scope.cancel()
      runBlocking { FileWatcherUtil.reset(eel) }
    }
  }

  private fun notifyChange(change: PathChange, data: DevcontainerData) {
    val root: String = data.findPath(change.path) ?: return
    when (change.type) {
      FileChangeType.CHANGED -> myNotificationSink.notifyDirtyPath(root)
      FileChangeType.CREATED, FileChangeType.DELETED -> myNotificationSink.notifyPathCreatedOrDeleted(root)
    }
  }

  private fun shutdownWatcherJobs() {
    watchedDevcontainers.values.forEach { it.cancel() }
    watchedDevcontainers.clear()
  }

  override fun startup() {}

  override fun shutdown(): Unit = shutdownWatcherJobs()

  companion object {
    fun useEelFileWatcher(): Boolean {
      return if (ApplicationManager.getApplication().isUnitTestMode) {
        java.lang.Boolean.getBoolean("use.eel.file.watcher")
      } else Registry.`is`("use.eel.file.watcher", false)
    }
  }
}

private fun String.getDevcontainerPathInfo(): DevcontainerPathInfo? {
  val path = this.toNioPathOrNull()?.asEelPath() ?: return null
  val descriptor = path.descriptor
  return if (descriptor is LocalEelDescriptor) null else DevcontainerPathInfo(descriptor, path.toString(), this)
}

private data class DevcontainerPathInfo(val descriptor: EelDescriptor, val path: String, val absolutePath: String)

private data class WatchedDevcontainer(val data: DevcontainerData, val cancel: () -> Unit)