// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2.impl

// @spec ../../../../../../../../spec/settings-writes.spec.md
// @spec ../../../../../../../../spec/snapshot-cache.spec.md

import com.intellij.ide.AppLifecycleListener
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.TaskCancellation
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.platform.settings.v2.SettingsContext
import com.intellij.platform.settings.v2.SettingsVault

/**
 * Flushes every pending debounced settings write to disk before the application closes, so a just-made change is
 * not lost. Then it writes the application snapshot cache if it has changes. This happens after the exit is decided
 * and before the settings are saved, while the vault and every provider are still alive. The progress is modal and
 * non-cancellable, because the write must complete and may block briefly.
 *
 * The flush drains the application scope, and so every storage unit, because [SettingsVault.flush] drains by context.
 * `SettingsProjectListener` of the backend module handles a project close. Both flush a project that is still open at
 * the shutdown, which is idempotent.
 *
 * Registered as an application listener in `intellij.platform.settings.v2.shared.xml`.
 */
internal class SettingsAppLifecycleListener : AppLifecycleListener {
  override fun beforeAppWillBeClosed(isRestart: Boolean) {
    runWithModalProgressBlocking(
      ModalTaskOwner.guess(),
      SettingsV2Bundle.message("progress.title.saving.settings"),
      TaskCancellation.nonCancellable(),
    ) {
      val vault = SettingsVault.getInstance()
      vault.flush(SettingsContext.app())
      (vault as SettingsVaultImpl).saveAppCacheIfDirty()
    }
  }
}
