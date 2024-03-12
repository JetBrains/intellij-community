// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io.mvstore

import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.ArrayUtilRt
import org.h2.mvstore.MVMap
import org.h2.mvstore.MVStore
import org.jetbrains.annotations.ApiStatus.Internal
import java.nio.channels.ClosedChannelException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.time.Duration.Companion.seconds

@Internal
fun markMvStoreDbAsInvalid(file: Path) {
  if (Files.exists(file)) {
    Files.write(getInvalidateMarkerFile(file), ArrayUtilRt.EMPTY_BYTE_ARRAY, StandardOpenOption.WRITE, StandardOpenOption.CREATE)
  }
}

@Internal
fun <K, V> openOrResetMap(
  store: MVStore,
  name: String,
  mapBuilder: MVMap.Builder<K, V>,
  logSupplier: () -> Logger,
): MVMap<K, V> {
  try {
    return store.openMap(name, mapBuilder)
  }
  catch (e: Throwable) {
    logSupplier().error("Cannot open map $name, map will be removed", e)
    try {
      store.removeMap(name)
    }
    catch (e2: Throwable) {
      e.addSuppressed(e2)
    }
  }
  return store.openMap(name, mapBuilder)
}

@Internal
fun createOrResetMvStore(file: Path, logSupplier: () -> Logger): MVStore {
  val markerFile = getInvalidateMarkerFile(file)
  if (Files.exists(markerFile)) {
    Files.deleteIfExists(file)
    Files.deleteIfExists(markerFile)
  }

  file.parent?.let { Files.createDirectories(it) }
  try {
    return tryOpenMvStore(file, logSupplier)
  }
  catch (e: Throwable) {
    logSupplier().warn("Cannot open cache state storage, will be recreated", e)
  }

  Files.deleteIfExists(file)
  return tryOpenMvStore(file, logSupplier)
}

private fun getInvalidateMarkerFile(file: Path): Path = file.resolveSibling("${file.fileName}.invalidated")

private fun tryOpenMvStore(file: Path, logSupplier: () -> Logger): MVStore {
  val storeErrorHandler = StoreErrorHandler(file, logSupplier)
  val store = MVStore.Builder()
    .fileName(file.toAbsolutePath().toString())
    .backgroundExceptionHandler(storeErrorHandler)
    // avoid extra thread - db maintainer should use coroutines
    .autoCommitDisabled()
    // default cache size is 16MB
    .cacheSize(8)
    .open()
  storeErrorHandler.isStoreOpened = true
  // versioning isn't required, otherwise the file size will be larger than needed
  store.setVersionsToKeep(0)
  return store
}

private class StoreErrorHandler(private val dbFile: Path, private val logSupplier: () -> Logger) : Thread.UncaughtExceptionHandler {
  @JvmField
  var isStoreOpened: Boolean = false

  override fun uncaughtException(t: Thread, e: Throwable) {
    val log = logSupplier()
    if (isStoreOpened) {
      log.error("Store error (db=$dbFile)", e)
    }
    else {
      log.warn("Store will be recreated (db=$dbFile)", e)
    }
  }
}

@Internal
fun compactMvStore(store: MVStore, logSupplier: () -> Logger) {
  try {
    store.compactFile(3.seconds.inWholeMilliseconds.toInt())
  }
  catch (e: RuntimeException) {
    /** see [org.h2.mvstore.FileStore.compact] */
    val cause = e.cause
    if (cause !is InterruptedException && cause !is ClosedChannelException) {
      logSupplier().warn("Cannot compact", e)
    }
  }
}