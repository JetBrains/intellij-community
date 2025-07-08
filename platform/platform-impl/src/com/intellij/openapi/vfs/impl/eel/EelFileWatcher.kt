// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.impl.eel

import com.intellij.openapi.util.io.toNioPathOrNull
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.impl.eel.EelFileWatcher.Companion.LOG
import com.intellij.openapi.vfs.local.FileWatcherNotificationSink
import com.intellij.openapi.vfs.local.PluggableFileWatcher
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.fs.EelFileSystemApi.FileChangeType
import com.intellij.platform.eel.fs.EelFileSystemApi.PathChange
import com.intellij.platform.eel.fs.WatchOptionsBuilder
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.platform.eel.provider.asEelPath
import com.intellij.platform.eel.provider.upgradeBlocking
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.Volatile

@ApiStatus.Internal
class EelFileWatcher : PluggableFileWatcher() {

  private lateinit var myNotificationSink: FileWatcherNotificationSink
  private val myWatchedEels = ConcurrentHashMap<EelDescriptor, WatchedEel>()
  private val mySettingRoots = AtomicInteger(0)
  private val watchedOptions = setOf(FileChangeType.CHANGED, FileChangeType.CREATED, FileChangeType.DELETED)

  @Volatile
  private var myShuttingDown = false

  companion object {
      val LOG = com.intellij.openapi.diagnostic.logger<EelFileWatcher>()
  }

  override fun initialize(notificationSink: FileWatcherNotificationSink) {
    myNotificationSink = notificationSink
  }

  override fun dispose() {
    myShuttingDown = true
    shutdownWatcherJobs()
  }

  override fun isOperational(): Boolean = Registry.`is`("use.eel.file.watcher", false)

  override fun isSettingRoots(): Boolean = isOperational() && mySettingRoots.get() > 0

  override fun setWatchRoots(recursive: List<String>, flat: List<String>, shuttingDown: Boolean) {
    if (!isOperational) return
    val recursiveFiltered = filterAndNotifyManualWatchRoots(recursive)
    val flatFiltered = filterAndNotifyManualWatchRoots(recursive)
    if (recursiveFiltered.isEmpty() && flatFiltered.isEmpty()) return

    if (shuttingDown) {
      myShuttingDown = true
      shutdownWatcherJobs()
      return
    }

    val newData = HashMap<EelDescriptor, EelData>()
    sortRoots(recursiveFiltered, newData, true)
    sortRoots(flatFiltered, newData, false)

    newData.forEach { (key, incoming) ->
      val existing = myWatchedEels[key]
      when {
        existing == null -> myWatchedEels[key] = WatchedEel(incoming, setupWatcherJob(incoming))
        existing.data != incoming -> {
          existing.cancel()
          existing.data.reload(incoming)
          myWatchedEels[key] = WatchedEel(incoming, setupWatcherJob(incoming))
        }
        else -> myNotificationSink.notifyManualWatchRoots(this, existing.data.ignored)
      }
    }

    myWatchedEels.entries.removeIf { (key, value) ->
      if (!newData.containsKey(key)) {
        value.cancel()
        true
      } else false
    }
  }

  private fun filterAndNotifyManualWatchRoots(all: List<String>): List<EelPathInfo> {
    val (result, ignored) = all.map { it to it.getDevcontainerPathInfo() }.partition { it.second != null }
    myNotificationSink.notifyManualWatchRoots(this, ignored.map { it.first })

    return result.mapNotNull { it.second }
  }

  private fun sortRoots(
    roots: List<EelPathInfo>,
    eelData: MutableMap<EelDescriptor, EelData>,
    recursive: Boolean,
  ) {
    roots.forEach { info ->
      val descriptor = info.descriptor
      val data: EelData = eelData.computeIfAbsent(descriptor) { EelData(info.descriptor) }
      (if (recursive) data.recursive else data.flat)[info.path] = info.absolutePath
    }
  }

  @OptIn(DelicateCoroutinesApi::class)
  private fun setupWatcherJob(data: EelData): () -> Unit {
    if (myShuttingDown) return {}

    mySettingRoots.incrementAndGet()
    val scope = GlobalScope.childScope("IJentFileWatcher")
    val eel = data.descriptor.upgradeBlocking()

    val job = scope.launch {
      val flow = eel.fs.watchChanges()
      eel.fs.addWatchRoots(WatchOptionsBuilder().changeTypes(watchedOptions).paths(data.getWatchedPaths()).build())
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

  private fun notifyChange(change: PathChange, data: EelData) {
    val root: String = data.findPath(change.path) ?: return
    when (change.type) {
      FileChangeType.CHANGED -> myNotificationSink.notifyDirtyPath(root)
      FileChangeType.CREATED, FileChangeType.DELETED -> myNotificationSink.notifyPathCreatedOrDeleted(root)
    }
  }

  private fun shutdownWatcherJobs() {
    myWatchedEels.values.forEach { it.cancel() }
    myWatchedEels.clear()
  }

  override fun startup() {}

  override fun shutdown(): Unit = shutdownWatcherJobs()
}

private fun String.getDevcontainerPathInfo(): EelPathInfo? {
  val path = this.toNioPathOrNull()?.asEelPath() ?: return null
  val descriptor = path.descriptor
  return if (descriptor is LocalEelDescriptor) null else EelPathInfo(descriptor, path.toString(), this)
}

private data class EelPathInfo(val descriptor: EelDescriptor, val path: String, val absolutePath: String)

private class WatchedEel(val data: EelData, private val cancelCallback: () -> Unit) {
  fun cancel() {
    try {
      // May throw UnsupportedOperationException if FileWatcherUtil.reset(eel) is called
      cancelCallback()
    } catch (e: UnsupportedOperationException) {
      LOG.warn(e.message)
    }
  }
}