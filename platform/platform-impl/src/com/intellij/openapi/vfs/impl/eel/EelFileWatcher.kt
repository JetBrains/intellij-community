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
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.path.EelPathException
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.platform.eel.provider.asEelPath
import com.intellij.platform.eel.provider.asNioPath
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
      }
    }

    myWatchedEels.entries.removeIf { (key, value) ->
      if (!newData.containsKey(key)) {
        value.cancel()
        true
      } else false
    }
  }

  private fun filterAndNotifyManualWatchRoots(all: List<String>): List<EelPath> {
    val filtered = all
      .map(String::toNioPathOrNull)
      .map { nioPath -> nioPath?.asEelPath() }
      .map { eelPath ->
        // The original IntelliJ platform's file watcher is responsible for local files.
        if (eelPath == null || eelPath.descriptor == LocalEelDescriptor) null
        else eelPath
      }
    val ignoredPaths = all.zip(filtered).mapNotNull { (sourcePath, flag) -> sourcePath.takeIf { flag == null } }
    myNotificationSink.notifyManualWatchRoots(this, ignoredPaths)

    return filtered.filterNotNull()
  }

  private fun sortRoots(
    roots: List<EelPath>,
    eelData: MutableMap<EelDescriptor, EelData>,
    recursive: Boolean,
  ) {
    roots.forEach { path ->
      val data: EelData = eelData.computeIfAbsent(path.descriptor, ::EelData)
      (if (recursive) data.recursive else data.flat).add(path)
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
    val eelPath =
      try {
        EelPath.parse(change.path, data.descriptor)
      }
      catch (_: EelPathException) {
        return
      }

    if (!data.flat.contains(eelPath) && !data.recursive.any(eelPath::startsWith)){
      return
    }

    val mrfsPath: String = eelPath.asNioPath().toString()
    when (change.type) {
      FileChangeType.CHANGED -> myNotificationSink.notifyDirtyPath(mrfsPath)
      FileChangeType.CREATED, FileChangeType.DELETED -> myNotificationSink.notifyPathCreatedOrDeleted(mrfsPath)
    }
  }

  private fun shutdownWatcherJobs() {
    myWatchedEels.values.forEach { it.cancel() }
    myWatchedEels.clear()
  }

  override fun startup() {}

  override fun shutdown(): Unit = shutdownWatcherJobs()
}

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