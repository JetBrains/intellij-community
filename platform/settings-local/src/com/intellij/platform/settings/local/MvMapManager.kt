// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package com.intellij.platform.settings.local

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.diagnostic.logger
import com.intellij.util.io.mvstore.createOrResetMvStore
import com.intellij.util.io.mvstore.openOrResetMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.h2.mvstore.MVMap
import org.h2.mvstore.MVStore
import org.h2.mvstore.type.ByteArrayDataType
import org.jetbrains.annotations.TestOnly
import java.nio.file.Path
import kotlin.time.Duration.Companion.minutes
import kotlin.time.DurationUnit
import kotlin.time.toDuration

private fun nowAsDuration() = System.currentTimeMillis().toDuration(DurationUnit.MILLISECONDS)

internal class MvStoreManager(private val readOnly: Boolean = false) {
  // we save only once every 2 minutes, and not earlier than 2 minutes after the start
  private var lastSaved = nowAsDuration()
  private val store = createOrResetMvStore(getDatabaseFile(), readOnly) { logger<MvMapManager>() }

  fun openMap(name: String): MvMapManager = MvMapManager(openMap(store, name))

  suspend fun save() {
    if (readOnly) { // Work around IJPL-173020: let's ignore attempts to save to a read-only store
      return
    }

    // Save upon exit.
    // This function will be executed under progress.
    // If saving is skipped here, `close` would be invoked on `close`, which will trigger `save`.
    // This in turn would lead to saving in EDT — that must be avoided.
    val exitInProgress = ApplicationManager.getApplication().isExitInProgress
    if (!exitInProgress && (nowAsDuration() - lastSaved) < 2.minutes) {
      return
    }

    withContext(Dispatchers.IO) {
      store.commit()
      lastSaved = nowAsDuration()
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

  fun hasKeyStartsWith(key: String): Boolean {
    val ceilingKey = map.ceilingKey(key)
    return ceilingKey != null && ceilingKey.startsWith(key)
  }
}

private fun getDatabaseFile(): Path = PathManager.getConfigDir().resolve(StoragePathMacros.APP_INTERNAL_STATE_DB)

private fun openMap(store: MVStore, name: String): MVMap<String, ByteArray> {
  val mapBuilder = MVMap.Builder<String, ByteArray>()
  mapBuilder.setKeyType(ModernStringDataType)
  mapBuilder.setValueType(ByteArrayDataType.INSTANCE)
  return openOrResetMap(store = store, name = name, mapBuilder = mapBuilder) { logger<MvMapManager>() }
}