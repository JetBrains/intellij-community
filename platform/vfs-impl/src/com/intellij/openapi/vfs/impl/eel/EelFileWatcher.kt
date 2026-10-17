// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.impl.eel

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.io.toNioPathOrNull
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.impl.eel.EelFileWatcher.Companion.LOG
import com.intellij.openapi.vfs.local.FileWatcherNotificationSink
import com.intellij.openapi.vfs.local.PluggableFileWatcher
import com.intellij.openapi.vfs.newvfs.ManagingFS
import com.intellij.openapi.vfs.newvfs.NewVirtualFile
import com.intellij.openapi.vfs.newvfs.RefreshQueue
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
    // Application disposal runs on EDT, and a remote unwatch request may never complete if EEL is already unavailable.
    shutdownWatcherJobs(resetRemoteWatches = false)
  }

  override fun isOperational(): Boolean = Registry.`is`("use.eel.file.watcher", false) && isEnabled

  override fun isSettingRoots(): Boolean = isOperational() && mySettingRoots.get() > 0

  override fun setWatchRoots(recursive: List<String>, flat: List<String>, shuttingDown: Boolean) {
    if (!isOperational) return
    if (shuttingDown) {
      myShuttingDown = true
      shutdownWatcherJobs(resetRemoteWatches = false)
      return
    }
    myRetryJob?.cancel()
    myRetryJob = null
    val recursiveFiltered = filterAndNotifyManualWatchRoots(recursive)
    val flatFiltered = filterAndNotifyManualWatchRoots(flat)
    if (recursiveFiltered.isEmpty() && flatFiltered.isEmpty()) {
      // EEL descriptor providers may not be ready during early startup.
      // Schedule a one-time delayed retry to re-resolve paths that could not be matched.
      if (recursive.isNotEmpty() || flat.isNotEmpty()) {
        scheduleRetryForUnresolvedRoots(recursive, flat)
      }
      return
    }

    val newData = HashMap<EelDescriptor, EelData>()
    sortRoots(recursiveFiltered, newData, true)
    sortRoots(flatFiltered, newData, false)

    synchronized(myWatchedEels) {
      newData.forEach { (key, incoming) ->
        val existing = myWatchedEels[key]
        when {
          existing == null -> myWatchedEels[key] = WatchedEel(incoming, setupWatcherJob(incoming).cancel)
          existing.data != incoming -> {
            existing.cancel()
            existing.data.reload(incoming)
            myWatchedEels[key] = WatchedEel(incoming, setupWatcherJob(incoming).cancel)
          }
        }
      }
      removeObsoleteWatchedEels(newData.keys)
    }
  }

  /**
   * Starts the watch of [descriptor] again, on the current session of the environment.
   *
   * The watch stream ends with the session that it runs on, and nothing starts it again.
   * A caller that deployed the session again calls this function.
   * The new watch reports its roots as reset and refreshes the local roots, so every dirty file is scanned too.
   * The platform also reports a reset after a restart of the native watcher.
   * Usually a watcher does not refresh. This one does, because a reset that nobody acts on is worthless.
   *
   * The function connects to the environment on the calling thread, so call it from a background thread.
   *
   * @return the job that ends after the refresh or after a failed start, or `null` when nothing is watched for [descriptor]
   */
  fun restartWatching(descriptor: EelDescriptor): Job? = synchronized(myWatchedEels) {
    val watched = myWatchedEels[descriptor] ?: return null
    // the old session is gone, so an unwatch on it fails
    watched.cancel(resetRemoteWatches = false)
    val setup = setupWatcherJob(watched.data) { resetAndRefreshRoots(watched.data) }
    myWatchedEels[descriptor] = WatchedEel(watched.data, setup.cancel)
    setup.job
  }

  private suspend fun resetAndRefreshRoots(data: EelData) {
    val roots = (data.recursive + data.flat).map { it.asNioPath() }
    roots.forEach { myNotificationSink.notifyReset(it.toString()) }
    val files = roots.mapNotNull { LocalFileSystem.getInstance().findFileByNioFile(it) }.filterIsInstance<NewVirtualFile>()
    // the sink applies a reset later, so the roots are marked here before the refresh
    readAction { files.forEach { it.markDirtyRecursively() } }
    RefreshQueue.getInstance().refresh(true, ManagingFS.getInstance().localRoots.toList())
  }

  private fun removeObsoleteWatchedEels(activeDescriptors: Set<EelDescriptor>) {
    myWatchedEels.entries.removeIf { (key, value) ->
      if (!activeDescriptors.contains(key)) {
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
  private fun setupWatcherJob(data: EelData, onStarted: suspend () -> Unit = {}): WatcherSetup {
    if (myShuttingDown) return WatcherSetup.NONE

    mySettingRoots.incrementAndGet()
    val scope = GlobalScope.childScope("IJentFileWatcher")

    try {
      val eel = data.descriptor.toEelApiBlocking()

      val job = scope.launch {
        try {
          val flow = eel.fs.watchChanges()
          eel.fs.addWatchRoots(WatchOptionsBuilder().changeTypes(watchedOptions).paths(data.getWatchedPaths()).build())
          scope.launch {
            try {
              flow.collect { notifyChange(it, data) }
            }
            catch (e: CancellationException) {
              throw e
            }
            catch (e: Exception) {
              LOG.debug("File watcher flow terminated for ${data.descriptor}: ${e.message}")
            }
          }
        }
        catch (e: CancellationException) {
          throw e 
        }
        catch (e: Exception) {
          LOG.warn("Failed to start watching for ${data.descriptor}", e)
          reportManualWatchRoots(data)
          return@launch
        }
        finally {
          mySettingRoots.decrementAndGet()
        }
        onStarted()
      }

      return WatcherSetup(job) { resetRemoteWatches ->
        job.cancel()
        scope.cancel()
        if (resetRemoteWatches) {
          runBlocking { FileWatcherUtil.reset(eel) }
        }
      }
    }
    catch (e: Exception) {
      LOG.warn("Failed to setup watcher for ${data.descriptor}", e)
      mySettingRoots.decrementAndGet()
      scope.cancel()
      reportManualWatchRoots(data)
      return WatcherSetup.NONE
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

      synchronized(myWatchedEels) {
        newData.forEach { (key, incoming) ->
          if (!myWatchedEels.containsKey(key)) {
            myWatchedEels[key] = WatchedEel(incoming, setupWatcherJob(incoming).cancel)
          }
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

  private fun shutdownWatcherJobs(resetRemoteWatches: Boolean = true) {
    myRetryJob?.cancel()
    myRetryJob = null
    myWatchedEels.values.forEach { it.cancel(resetRemoteWatches) }
    myWatchedEels.clear()
  }

  @TestOnly
  fun addWatchedEelForTest(descriptor: EelDescriptor, cancelCallback: (Boolean) -> Unit) {
    check(myWatchedEels.putIfAbsent(descriptor, WatchedEel(EelData(descriptor), cancelCallback)) == null)
  }

  @TestOnly
  fun retainWatchedEelsForTest(activeDescriptors: Set<EelDescriptor>) {
    removeObsoleteWatchedEels(activeDescriptors)
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

private class WatchedEel(val data: EelData, private val cancelCallback: (Boolean) -> Unit) {
  fun cancel(resetRemoteWatches: Boolean = true) {
    try {
      // May throw UnsupportedOperationException if FileWatcherUtil.reset(eel) is called
      cancelCallback(resetRemoteWatches)
    } catch (e: UnsupportedOperationException) {
      LOG.warn(e.message)
    }
  }
}

/** The [job] that starts a watch, and the callback that cancels the watch. */
private class WatcherSetup(val job: Job, val cancel: (Boolean) -> Unit) {
  companion object {
    /** A setup that started no watch. Its job is complete. */
    val NONE: WatcherSetup = WatcherSetup(Job().apply { complete() }) { _ -> }
  }
}