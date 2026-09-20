// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io.mvstore

import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.ArrayUtilRt
import org.h2.mvstore.MVMap
import org.h2.mvstore.MVStore
import org.jetbrains.annotations.ApiStatus.Internal
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

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

/**
 * Opens the store at [file], or recreates it when it is corrupt.
 * [cacheSizeMb] sizes the on-heap page cache of the store.
 */
@Internal
fun createOrResetMvStore(file: Path?, readOnly: Boolean = false, cacheSizeMb: Int = 8, logSupplier: () -> Logger): MVStore {
  // If read-only and DB does not yet exist, create an in-memory DB
  if (file == null || (readOnly && Files.notExists(file))) {
    // in-memory
    return tryOpenMvStore(file = null, readOnly = readOnly, cacheSizeMb = cacheSizeMb, logSupplier = logSupplier)
  }

  val markerFile = getInvalidateMarkerFile(file)
  if (Files.exists(markerFile)) {
    Files.deleteIfExists(file)
    Files.deleteIfExists(markerFile)
  }

  file.parent?.let { Files.createDirectories(it) }
  try {
    return tryOpenMvStore(file, readOnly, cacheSizeMb, logSupplier)
  }
  catch (e: Throwable) {
    logSupplier().warn("Cannot open cache state storage, will be recreated", e)
  }
  if (readOnly) {
    // in read-only mode the store won't load without a file, so use in-memory store in such a case
    return tryOpenMvStore(file = null, readOnly, cacheSizeMb, logSupplier)
  }

  Files.deleteIfExists(file)
  return tryOpenMvStore(file, readOnly, cacheSizeMb, logSupplier)
}

private fun getInvalidateMarkerFile(file: Path): Path = file.resolveSibling("${file.fileName}.invalidated")

private fun tryOpenMvStore(file: Path?, readOnly: Boolean, cacheSizeMb: Int, logSupplier: () -> Logger): MVStore {
  val storeErrorHandler = StoreErrorHandler(file, logSupplier)
  val store = MVStore.Builder()
    .fileName(file?.toAbsolutePath()?.toString())
    .backgroundExceptionHandler(storeErrorHandler)
    // avoid extra thread - db maintainer should use coroutines
    .autoCommitDisabled()
    // the page cache is on-heap; the caller sizes it per store (the MVStore default is 16 MB)
    .cacheSize(cacheSizeMb)
    .let {
      if (readOnly) it.readOnly() else it
    }
    .open()
  storeErrorHandler.isStoreOpened = true
  // versioning isn't required, otherwise the file size will be larger than needed
  store.setVersionsToKeep(0)
  return store
}

private class StoreErrorHandler(private val dbFile: Path?, private val logSupplier: () -> Logger) : Thread.UncaughtExceptionHandler {
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