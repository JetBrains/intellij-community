// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileChooser.universal

import com.intellij.openapi.diagnostic.Logger
import com.intellij.platform.eel.fs.EelFileSystemApi.FileChangeType
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.file.ClosedWatchServiceException
import java.nio.file.Files
import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds.ENTRY_CREATE
import java.nio.file.StandardWatchEventKinds.ENTRY_DELETE
import java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY
import java.nio.file.WatchKey
import java.nio.file.WatchService
import java.util.concurrent.ConcurrentHashMap

internal class LocalFileWatcherAdapter : FileWatcherAdapter {
  @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
  private val pumpScope = GlobalScope.childScope("LocalFileWatcherAdapter", supervisor = true)
  private val watchKeys = ConcurrentHashMap<Path, WatchKey>()
  private val flows = ConcurrentHashMap<Path, MutableSharedFlow<FileChangeType>>()
  private val lazyWatchService = lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    FileSystems.getDefault().newWatchService().also { service ->
      pumpScope.launch(Dispatchers.IO) { pump(service) }
    }
  }
  private val watchService: WatchService by lazyWatchService

  override suspend fun subscribe(path: Path): Flow<FileChangeType>? {
    if (!Files.isDirectory(path)) return null
    val flow = flows.computeIfAbsent(path) {
      MutableSharedFlow(extraBufferCapacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    }
    try {
      if (!watchKeys.containsKey(path)) {
        val key = withContext(Dispatchers.IO) {
          path.register(watchService, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY)
        }
        val existing = watchKeys.putIfAbsent(path, key)
        if (existing != null) key.cancel()
      }
    }
    catch (e: Exception) {
      LOG.debug("Cannot register watch service for $path", e)
      flows.remove(path)
      return null
    }
    return flow
  }

  override suspend fun unsubscribe(path: Path) {
    watchKeys.remove(path)?.cancel()
    flows.remove(path)
  }

  override suspend fun stop() {
    pumpScope.cancel()
    if (lazyWatchService.isInitialized()) {
      try {
        withContext(Dispatchers.IO) {
          watchService.close()
        }
      }
      catch (e: Exception) {
        LOG.debug("Error closing watch service", e)
      }
    }
    watchKeys.values.forEach { it.cancel() }
    watchKeys.clear()
    flows.clear()
  }

  private fun pump(watchService: WatchService) {
    try {
      while (pumpScope.isActive) {
        val key = try {
          watchService.take()
        }
        catch (_: ClosedWatchServiceException) {
          return
        }
        catch (e: InterruptedException) {
          LOG.debug("Watch service interrupted", e)
          return
        }
        val watched = key.watchable() as? Path
        for (event in key.pollEvents()) {
          val change = when (event.kind()) {
            ENTRY_CREATE -> FileChangeType.CREATED
            ENTRY_DELETE -> FileChangeType.DELETED
            ENTRY_MODIFY -> FileChangeType.CHANGED
            else -> null
          } ?: continue
          if (watched != null) {
            flows[watched]?.tryEmit(change)
          }
        }
        if (!key.reset()) {
          // Registration is no longer valid (e.g., directory deleted).
          if (watched != null) {
            watchKeys.remove(watched)
            flows.remove(watched)
          }
          else {
            watchKeys.entries.removeIf { it.value === key }
          }
        }
      }
    }
    catch (_: ClosedWatchServiceException) {
      // expected on close
    }
  }

  companion object {
    private val LOG = Logger.getInstance(LocalFileWatcherAdapter::class.java)
  }
}