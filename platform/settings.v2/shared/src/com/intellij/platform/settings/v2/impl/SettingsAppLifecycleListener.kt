// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2.impl

// @spec ../../../../../../../../spec/settings-writes.spec.md
// @spec ../../../../../../../../spec/snapshot-cache.spec.md

import com.intellij.ide.AppLifecycleListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.runBlockingMaybeCancellable

private val LOG = logger<SettingsAppLifecycleListener>()

/**
 * Saves the application settings before the application closes, so a just-made change is not lost. The save is
 * [saveAppSettingsAndCache]. This happens after the exit is decided and before the settings are saved, while the
 * vault and every provider are still alive.
 *
 * The platform calls [beforeAppWillBeClosed] on the EDT under the write-intent lock. A [ShutdownSaver] runs the save
 * when the product has one. Without a saver, the listener runs the save with a blocking call on the EDT and shows no
 * progress. The caller can have no current job, so the call is [runBlockingMaybeCancellable]. Only a headless product
 * can have no saver: a product with a UI must include `intellij.platform.settings.v2.shared.essential`. Else the
 * listener logs an error, and then it saves. The vault logs a store that fails to save, and the platform logs an
 * exception that leaves the listener. So the close continues.
 *
 * `SettingsProjectListener` of the backend baseline module handles a project close. Both flush a project that is
 * still open at the shutdown, which is idempotent.
 *
 * Registered as an application listener in `intellij.platform.settings.v2.shared.xml`.
 */
internal class SettingsAppLifecycleListener : AppLifecycleListener {
  override fun beforeAppWillBeClosed(isRestart: Boolean) {
    val saver = ShutdownSaver.getInstanceOrNull()
    if (saver != null) {
      saver.save()
    }
    else {
      if (!ApplicationManager.getApplication().isHeadlessEnvironment) {
        LOG.error("No ShutdownSaver is registered. A product that is not headless must include the module " +
                  "'intellij.platform.settings.v2.shared.essential'.")
      }
      runBlockingMaybeCancellable { saveAppSettingsAndCache() }
    }
  }
}
