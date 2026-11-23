// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Internal

package com.intellij.platform.settings.v2.impl

// @spec ../../../../../../../../spec/settings-writes.spec.md
// @spec ../../../../../../../../spec/snapshot-cache.spec.md

import com.intellij.openapi.components.serviceOrNull
import com.intellij.platform.settings.v2.SettingsContext
import com.intellij.platform.settings.v2.SettingsVault
import org.jetbrains.annotations.ApiStatus.Internal

/**
 * Runs the shutdown save: [saveAppSettingsAndCache] when the application closes. [SettingsAppLifecycleListener] asks
 * for it. An implementation decides only how the save runs, not what it saves.
 *
 * The service is optional, and the vault module has no implementation. The only implementation is
 * `ModalShutdownSaver` in `intellij.platform.settings.v2.shared.essential`. It shows a modal progress, and that needs
 * modules outside `core.platform`. A product without that module has no saver. Then the listener runs the save
 * itself with a blocking call on the EDT and shows no progress.
 */
@Internal
interface ShutdownSaver {
  /**
   * Runs [saveAppSettingsAndCache] and returns when the save is complete. The caller is on the EDT and holds the
   * write-intent lock.
   */
  fun save()

  companion object {
    /**
     * Returns the saver of the product.
     *
     * @return the saver, or `null` when no loaded module registers one
     */
    fun getInstanceOrNull(): ShutdownSaver? = serviceOrNull<ShutdownSaver>()
  }
}

/**
 * Flushes every pending write of the application scope to the disk, then writes the application snapshot cache if it
 * has changes. This is the body of the shutdown save. A [ShutdownSaver] or [SettingsAppLifecycleListener] runs it.
 *
 * The flush drains the application scope, and so every storage unit, because [SettingsVault.flush] drains by context.
 */
suspend fun saveAppSettingsAndCache() {
  val vault = SettingsVault.getInstance()
  vault.flush(SettingsContext.app())
  (vault as SettingsVaultImpl).saveAppCacheIfDirty()
}
