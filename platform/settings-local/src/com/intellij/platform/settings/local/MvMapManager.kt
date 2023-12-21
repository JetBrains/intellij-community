// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package com.intellij.platform.settings.local

import com.intellij.openapi.application.PathManager
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

internal class MvStoreManager {
  // yes - save is ignored first 5 minutes
  private var lastSaved: Duration = nowAsDuration()
  // compact only once per-app launch
  private var isCompacted = AtomicBoolean(false)

  private val store: MVStore = createOrResetStore(getDatabaseFile())

  fun openMap(name: String): MvMapManager = MvMapManager(openMap(store, name))

  suspend fun save() {
    if ((nowAsDuration() - lastSaved) < 5.minutes) {
      return
    }

    // tryCommit - do not commit if store is locked (e.g., another commit for some reason is called or another write operation)
    withContext(Dispatchers.IO) {
      store.tryCommit()

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
        store.compactFile(30.seconds.inWholeMilliseconds.toInt())
      }
      catch (e: RuntimeException) {
        /** see [org.h2.mvstore.FileStore.compact] */
        if (e.cause !is InterruptedException) {
          thisLogger().warn("Cannot compact", e)
        }
      }
    }
  }

  fun close() {
    store.close()
  }

  @TestOnly
  fun clear() {
    for (mapName in store.mapNames) {
      store.openMap<Any, Any>(mapName).clear()
    }
  }
}

/**
 * * Faster than PHM (`kv-store-benchmark` project).
 * * Grouped on disk close to each other as keys are sorted
 * * One database file instead of several.
 * * Several maps in a one file (so, we can store several versions in a one file).
 */
// see DbConverter
internal class MvMapManager(private val map: MVMap<String, ByteArray>) {
  fun get(key: String): ByteArray? = map.get(key)

  fun remove(key: String) {
    map.remove(key)
  }

  fun invalidate() {
    val file = getDatabaseFile()
    if (Files.exists(file)) {
      Files.write(file.parent.resolve(".invalidated"), ArrayUtilRt.EMPTY_BYTE_ARRAY, StandardOpenOption.WRITE, StandardOpenOption.CREATE)
    }
  }

  fun clear() {
    map.clear()
  }

  fun put(key: String, value: ByteArray?) {
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

private fun getDatabaseFile(): Path = PathManager.getConfigDir().resolve("app-internal-state.db")

private fun createOrResetStore(file: Path): MVStore {
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
    logger<MvMapManager>().warn("Cannot open cache state storage, will be recreated", e)
  }

  NioFiles.deleteRecursively(file)
  return openStore(file)
}

private fun openStore(file: Path): MVStore {
  val storeErrorHandler = StoreErrorHandler(file) { logger<MvMapManager>() }
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
  return store
}

private fun openMap(store: MVStore, name: String): MVMap<String, ByteArray> {
  val mapBuilder = MVMap.Builder<String, ByteArray>()
  mapBuilder.setKeyType(ModernStringDataType)
  mapBuilder.setValueType(ByteArrayDataType.INSTANCE)

  try {
    return store.openMap(name, mapBuilder)
  }
  catch (e: Throwable) {
    logger<MvMapManager>().error("Cannot open map $name, map will be removed", e)
    try {
      store.removeMap(name)
    }
    catch (e2: Throwable) {
      e.addSuppressed(e2)
    }
  }
  return store.openMap(name, mapBuilder)
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
