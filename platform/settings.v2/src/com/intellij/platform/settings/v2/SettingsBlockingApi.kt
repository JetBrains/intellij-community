// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2

// @spec ../../../../../../spec/settings-writes.spec.md

import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresBlockingContext
import org.jetbrains.annotations.ApiStatus.Experimental

/**
 * Writes a setting from code that cannot suspend, such as Java code or a driver call. A read needs no bridge:
 * [SettingsKey.get] is a plain call. Kotlin code in a coroutine uses [SettingsKey.set] instead (ADR 0032).
 */
@Experimental
object SettingsBlockingApi {
  /**
   * Writes [value] for [key] at [context] and blocks until the write is applied, as [SettingsKey.set] does. Call it
   * on a background thread, never on the EDT. A thread with a job or a progress indicator cancels the write with it.
   * A thread without one, such as a driver call, is not cancellable.
   *
   * @throws IllegalStateException when no layer takes the write, see [SettingsKey.set]
   * @throws ReadOnlySettingsException when the key is read-only
   * @throws SettingsValidationException when [value] fails the validator of the key
   */
  @JvmStatic
  @RequiresBackgroundThread
  @RequiresBlockingContext
  fun <T> set(key: SettingsKey<T>, context: SettingsContext, value: T) {
    runBlockingMaybeCancellable { key[context] = value }
  }
}
