// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package com.intellij.platform.settings.local

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.appSystemDir
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.io.NioFiles
import com.intellij.util.ArrayUtilRt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import org.h2.mvstore.MVMap
import org.h2.mvstore.MVStore
import org.h2.mvstore.type.ByteArrayDataType
import org.h2.mvstore.type.StringDataType
import org.jetbrains.annotations.TestOnly
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.DurationUnit
import kotlin.time.toDuration

/**
 * * Faster than PHM (`kv-store-benchmark` project).
 * * Grouped on disk close to each other as keys are sorted
 * * One database file instead of several.
 * * Several maps in a one file (so, we can store several versions in a one file).
 */
// see DbConverter
internal class MvStoreStorage(coroutineScope: CoroutineScope) : Storage {
  private val map: MVMap<String, ByteArray> = createOrResetStore(getDatabaseFile())

  private var lastSaved: Duration = Duration.ZERO

  init {
    if (!ApplicationManager.getApplication().isUnitTestMode) {
      coroutineScope.coroutineContext.job.invokeOnCompletion {
        // on completion
        map.store.close()
      }
    }
  }

  override fun get(key: String): ByteArray? = map.get(key)

  override fun remove(key: String) {
    map.remove(key)
  }

  override fun put(key: String, bytes: ByteArray?) {
    map.put(key, bytes)
  }

  override suspend fun save() {
    if ((System.currentTimeMillis().toDuration(DurationUnit.MILLISECONDS) - lastSaved) < 5.minutes) {
      return
    }

    // tryCommit - do not commit if store is locked (e.g., another commit for some reason is called or another write operation)
    withContext(Dispatchers.IO) {
      map.store.tryCommit()
    }
    lastSaved = System.currentTimeMillis().toDuration(DurationUnit.MILLISECONDS)
  }

  override fun invalidate() {
    val file = getDatabaseFile()
    if (Files.exists(file)) {
      Files.write(file.parent.resolve(".invalidated"), ArrayUtilRt.EMPTY_BYTE_ARRAY, StandardOpenOption.WRITE, StandardOpenOption.CREATE)
    }
  }

  @TestOnly
  override fun clear() {
    map.clear()
  }
}

private fun getDatabaseFile(): Path = appSystemDir.resolve("app-cache-settings.db")

private fun createOrResetStore(file: Path): MVMap<String, ByteArray> {
  val parentDir = file.parent
  val markerFile = parentDir.resolve(".invalidated")
  if (Files.exists(markerFile)) {
    NioFiles.deleteRecursively(file)
    Files.deleteIfExists(markerFile)
  }

  Files.createDirectories(parentDir)

  try {
    return openStore(file)
  }
  catch (e: Throwable) {
    logger<MvStoreStorage>().warn("Cannot open cache state storage, will be recreated", e)
  }

  NioFiles.deleteRecursively(file)
  return openStore(file)
}

private fun openStore(file: Path): MVMap<String, ByteArray> {
  val storeErrorHandler = StoreErrorHandler(file) { logger<MvStoreStorage>() }
  // default cache size is 16MB
  val store = MVStore.Builder()
    .fileName(file.toAbsolutePath().toString())
    .backgroundExceptionHandler(storeErrorHandler)
    // avoid extra thread - db maintainer should use coroutines
    .autoCommitDisabled()
    .open()
  storeErrorHandler.isStoreOpened = true

  val mapBuilder = MVMap.Builder<String, ByteArray>()
  mapBuilder.setKeyType(StringDataType.INSTANCE)
  mapBuilder.setValueType(ByteArrayDataType.INSTANCE)

  return store.openMap("cache_v1", mapBuilder)
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
