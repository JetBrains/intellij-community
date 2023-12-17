// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package com.intellij.platform.settings.local

import com.intellij.openapi.application.appSystemDir
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.io.NioFiles
import com.intellij.util.ArrayUtilRt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import org.h2.mvstore.MVMap
import org.h2.mvstore.MVStore
import org.h2.mvstore.type.ByteArrayDataType
import org.jetbrains.annotations.TestOnly
import org.jetbrains.annotations.VisibleForTesting
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit
import kotlin.time.toDuration

private fun nowAsDuration() = System.currentTimeMillis().toDuration(DurationUnit.MILLISECONDS)

/**
 * * Faster than PHM (`kv-store-benchmark` project).
 * * Grouped on disk close to each other as keys are sorted
 * * One database file instead of several.
 * * Several maps in a one file (so, we can store several versions in a one file).
 */
// see DbConverter
internal class MvStoreStorage : Storage {
  private val map: MVMap<String, ByteArray> = createOrResetStore(getDatabaseFile())

  // yes - save is ignored first 5 minutes
  private var lastSaved: Duration = nowAsDuration()
  // compact only once per-app launch
  private var isCompacted = AtomicBoolean(false)

  override fun close() {
    map.store.close()
  }

  override fun get(key: String): ByteArray? = map.get(key)

  override fun remove(key: String) {
    map.remove(key)
  }

  override fun put(key: String, bytes: ByteArray?) {
    // do not write if existing value equals to the old one
    putIfDiffers(key, bytes)
  }

  override suspend fun save() {
    if ((nowAsDuration() - lastSaved) < 5.minutes) {
      return
    }

    // tryCommit - do not commit if store is locked (e.g., another commit for some reason is called or another write operation)
    withContext(Dispatchers.IO) {
      map.store.tryCommit()

      ensureActive()

      if (isCompacted.compareAndSet(false, true)) {
        compactStore()
      }
    }

    lastSaved = nowAsDuration()
  }

  @VisibleForTesting
  suspend fun compactStore() {
    runInterruptible {
      try {
        map.store.compactFile(30.seconds.inWholeMilliseconds.toInt())
      }
      catch (e: RuntimeException) {
        /** see [org.h2.mvstore.FileStore.compact] */
        if (e.cause !is InterruptedException) {
          thisLogger().warn("Cannot compact", e)
        }
      }
    }
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

  fun hasKeyStartsWith(key: String): Boolean {
    val ceilingKey = map.ceilingKey(key)
    return ceilingKey != null && ceilingKey.startsWith(key)
  }

  fun putIfDiffers(key: String, value: ByteArray?) {
    map.operate(key, value, object : MVMap.DecisionMaker<ByteArray?>() {
      override fun decide(existingValue: ByteArray?, providedValue: ByteArray?): MVMap.Decision {
        if (existingValue.contentEquals(providedValue)) {
          return MVMap.Decision.ABORT
        }
        else {
          return MVMap.Decision.PUT
        }
      }
    })
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
  mapBuilder.setKeyType(ModernStringDataType)
  mapBuilder.setValueType(ByteArrayDataType.INSTANCE)

  // versioning isn't required, otherwise the file size will be larger than needed
  store.setVersionsToKeep(0)
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
