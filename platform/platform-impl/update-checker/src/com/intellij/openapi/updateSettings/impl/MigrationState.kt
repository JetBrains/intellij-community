// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl

import com.intellij.openapi.application.MigrationState
import com.intellij.openapi.application.deleteMigrationState
import com.intellij.openapi.application.migrationStateFile
import com.intellij.openapi.application.readMigrationState
import com.intellij.openapi.application.writeMigrationState
import org.jetbrains.annotations.ApiStatus
import java.io.IOException
import java.nio.file.Path

/** Keeps the state of [file] in memory. The first [get] reads the file. */
@ApiStatus.Internal
class MigrationStateStore(private val file: Path) {
  private val lock = Any()
  private var isLoaded = false
  private var state: MigrationState? = null

  fun get(): MigrationState? = synchronized(lock) {
    if (!isLoaded) {
      load()
    }
    state
  }

  /** Deletes the file and the content copy, for example when the Unified IDE is removed while the IDE runs. */
  fun clear() {
    synchronized(lock) {
      state = null
      isLoaded = true
      deleteMigrationState(file)
    }
  }

  /** Keeps [newState] in memory also when the write fails, so that the session works with it. */
  @Throws(IOException::class)
  fun set(newState: MigrationState) {
    synchronized(lock) {
      state = newState
      isLoaded = true
      writeMigrationState(newState, file)
    }
  }

  private fun load() {
    state = readMigrationState(file)
    isLoaded = true
  }
}

private val defaultStore by lazy { MigrationStateStore(migrationStateFile()) }

/** The state of the config dir of this IDE. */
internal fun migrationStateStore(): MigrationStateStore = defaultStore
