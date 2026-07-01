// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2.impl.cache

// @spec ../../../../../../../../../spec/snapshot-cache.spec.md

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.platform.settings.v2.SettingsVault
import com.intellij.platform.settings.v2.impl.SettingsVaultImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.TestOnly
import java.io.IOException
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.seconds

/**
 * Returns whether the automatic startup and close wiring of the snapshot cache is active. It is off in the unit-test
 * mode, where the shared system directory would leak state across `@TestApplication` runs. A test drives [SettingsSnapshotCache] against a temp file directly instead. Both guards use it: the
 * application guard in [SettingsVaultImpl] and the project guard in `ProjectSettingsCacheService` of the backend
 * module.
 */
fun isSnapshotCacheEnabled(): Boolean = !ApplicationManager.getApplication().isUnitTestMode

/**
 * Runs the snapshot cache of one scope: the application scope or one project. The cache is a pure accelerator
 * (ADR 0083). [load] seeds the vault from the disk at the startup or at a project open. The normal reload then
 * reconciles against the real sources. So a stale or missing cache is never a correctness problem, only a slower
 * first paint. For that reason the cache needs no durability guarantee. Its writes are best-effort and debounced with
 * a long window, and a missed save only gives a slightly older cache the next time.
 *
 * @param cacheFile the cache file of the scope (see [SettingsCacheStorage])
 * @param project the project whose layers this cache holds, or `null` for the application cache, which holds the
 *   layers whose context names no project. See [SettingsVaultImpl.dump].
 */
class SettingsSnapshotCache(
  private val cacheFile: Path,
  private val project: Project?,
) {
  private val vault: SettingsVaultImpl get() = SettingsVault.getInstance() as SettingsVaultImpl
  private val dirty = AtomicBoolean(false)
  private val saveMutex = Mutex()

  /**
   * Holds the layers that this cache last wrote or loaded. A save skips the write when the slice of this scope did not
   * change. The model-changed signal knows no scope, so an edit in another scope signals this cache with no change of
   * its dump. The compare of the dumped structure, not of the encoded bytes, lets an unchanged save skip the encode.
   */
  @Volatile
  private var lastDumped: List<CachedLayer>? = null

  /**
   * Seeds the vault from the cached bytes, if any. A missing, invalidated, unreadable, outdated or malformed cache
   * seeds nothing and does not fail. An unreadable, outdated or malformed cache is logged.
   */
  suspend fun load() {
    val bytes = withContext(Dispatchers.IO) { readOrNull() } ?: return
    val layers = SettingsCacheSerializer.decode(bytes) ?: return
    vault.installCached(layers)
    lastDumped = layers
  }

  /** Starts the debounced best-effort saver on [scope]: every model change marks the cache dirty and schedules a save. */
  @OptIn(FlowPreview::class)
  fun startSaving(scope: CoroutineScope) {
    scope.launch {
      vault.modelChangedSignal
        .onEach { dirty.set(true) }  // mark immediately so a close-time saveIfDirty sees a pending change
        .debounce(SAVE_DEBOUNCE)
        .collect { saveIfDirty() }
    }
  }

  /** Writes the current dump if a change is pending since the last save. The debounced saver and the close both call it. */
  suspend fun saveIfDirty() {
    if (dirty.get()) save()
  }

  /** Dumps and writes the scope now, with no dirty check and no debounce. A test cannot wait for the debounce window. */
  @TestOnly
  suspend fun saveForTest(): Unit = save()

  private suspend fun save(): Unit = saveMutex.withLock {
    dirty.set(false)  // clear first, so a change landing during the dump/write re-dirties for the next round
    val layers = vault.dump(project)
    if (layers == lastDumped) return  // the slice of this scope did not change, so skip the encode and the write
    val bytes = SettingsCacheSerializer.encode(layers)
    withContext(Dispatchers.IO) {
      try {
        SettingsCacheStorage.write(cacheFile, bytes)
        lastDumped = layers
      }
      catch (e: IOException) {
        LOG.warn("Failed to write settings cache $cacheFile", e)
      }
    }
  }

  private fun readOrNull(): ByteArray? =
    try {
      SettingsCacheStorage.read(cacheFile)
    }
    catch (e: IOException) {
      LOG.warn("Failed to read settings cache $cacheFile", e)
      null
    }

  private companion object {
    private val LOG = logger<SettingsSnapshotCache>()

    /** Holds the save debounce. It is long on purpose: the cache is an accelerator, not a durable store, so saves coalesce. */
    private val SAVE_DEBOUNCE = 5.seconds
  }
}
