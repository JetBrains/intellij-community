// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.impl.eel

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
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
import com.intellij.platform.eel.provider.toEelApiBlocking
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.Volatile
import kotlin.coroutines.cancellation.CancellationException

@ApiStatus.Internal
class EelFileWatcher : PluggableFileWatcher() {

  private lateinit var myNotificationSink: FileWatcherNotificationSink
  private val myWatchedEels = ConcurrentHashMap<EelDescriptor, WatchedEel>()
  private val mySettingRoots = AtomicInteger(0)
  private val watchedOptions = setOf(FileChangeType.CHANGED, FileChangeType.CREATED, FileChangeType.DELETED)
  private var isEnabled: Boolean = false

  @Volatile
  private var myShuttingDown = false

  @Volatile
  private var myRetryJob: Job? = null

  companion object {
      val LOG: Logger = logger<EelFileWatcher>()
      private const val RETRY_DELAY_MS = 10_000L
  }

  override fun initialize(notificationSink: FileWatcherNotificationSink) {
    myNotificationSink = notificationSink
    isEnabled = ApplicationManager.getApplication().let { app->
      !(app.isCommandLine || app.isUnitTestMode)
    }
  }

  override fun dispose() {
    myShuttingDown = true
    shutdownWatcherJobs()
  }

  override fun isOperational(): Boolean = Registry.`is`("use.eel.file.watcher", false) && isEnabled

  override fun isSettingRoots(): Boolean = isOperational() && mySettingRoots.get() > 0

  override fun setWatchRoots(recursive: List<String>, flat: List<String>, shuttingDown: Boolean) {
    if (!isOperational) return
    myRetryJob?.cancel()
    myRetryJob = null
    val recursiveFiltered = filterAndNotifyManualWatchRoots(recursive)
    val flatFiltered = filterAndNotifyManualWatchRoots(flat)
    if (recursiveFiltered.isEmpty() && flatFiltered.isEmpty()) {
      // EEL descriptor providers may not be ready during early startup.
      // Schedule a one-time delayed retry to re-resolve paths that could not be matched.
      if (!shuttingDown && (recursive.isNotEmpty() || flat.isNotEmpty())) {
        scheduleRetryForUnresolvedRoots(recursive, flat)
      }
      return
    }

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
      .map { nioPath ->
        try {
          nioPath?.asEelPath()
        }
        catch (e: EelPathException) {
          LOG.debug("Cannot convert path to EelPath: $nioPath", e)
          null
        }
      }
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

    try {
      val eel = data.descriptor.toEelApiBlocking()

      val job = scope.launch {
        try {
          val flow = eel.fs.watchChanges()
          eel.fs.addWatchRoots(WatchOptionsBuilder().changeTypes(watchedOptions).paths(data.getWatchedPaths()).build())
          scope.launch { flow.collect { notifyChange(it, data) } }
        }
        catch (e: CancellationException) {
          throw e 
        }
        catch (e: Exception) {
          LOG.warn("Failed to start watching for ${data.descriptor}", e)
          reportManualWatchRoots(data)
        }
        finally {
          mySettingRoots.decrementAndGet()
        }
      }

      return {
        job.cancel()
        scope.cancel()
        runBlocking { FileWatcherUtil.reset(eel) }
      }
    }
    catch (e: Exception) {
      LOG.warn("Failed to setup watcher for ${data.descriptor}", e)
      mySettingRoots.decrementAndGet()
      scope.cancel()
      reportManualWatchRoots(data)
      return {}
    }
  }

  /**
   * Schedules a one-time delayed retry to re-resolve watch roots.
   * This handles the case where EEL descriptor providers are not yet ready during early startup.
   */
  @OptIn(DelicateCoroutinesApi::class)
  private fun scheduleRetryForUnresolvedRoots(recursive: List<String>, flat: List<String>) {
    myRetryJob = GlobalScope.launch {
      delay(RETRY_DELAY_MS)
      if (myShuttingDown) return@launch

      val recursiveResolved = resolveNonLocalPaths(recursive)
      val flatResolved = resolveNonLocalPaths(flat)

      if (recursiveResolved.isEmpty() && flatResolved.isEmpty()) return@launch

      LOG.info("Delayed EEL watch root resolution succeeded for ${recursiveResolved.size + flatResolved.size} paths")

      val newData = HashMap<EelDescriptor, EelData>()
      sortRoots(recursiveResolved, newData, true)
      sortRoots(flatResolved, newData, false)

      newData.forEach { (key, incoming) ->
        if (!myWatchedEels.containsKey(key)) {
          myWatchedEels[key] = WatchedEel(incoming, setupWatcherJob(incoming))
        }
      }
    }
  }

  private fun resolveNonLocalPaths(paths: List<String>): List<EelPath> {
    return paths.mapNotNull { path ->
      try {
        val nioPath = path.toNioPathOrNull() ?: return@mapNotNull null
        val eelPath = nioPath.asEelPath()
        if (eelPath.descriptor != LocalEelDescriptor) eelPath else null
      }
      catch (_: EelPathException) {
        null
      }
    }
  }

  private fun reportManualWatchRoots(data: EelData) {
    val manualPaths = (data.recursive + data.flat).map { it.asNioPath().toString() }
    myNotificationSink.notifyManualWatchRoots(this, manualPaths)
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
    myRetryJob?.cancel()
    myRetryJob = null
    myWatchedEels.values.forEach { it.cancel() }
    myWatchedEels.clear()
  }

  @TestOnly
  override fun startup() {
    isEnabled = true
  }

  @TestOnly
  override fun shutdown() {
    isEnabled = false
    shutdownWatcherJobs()
  }
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