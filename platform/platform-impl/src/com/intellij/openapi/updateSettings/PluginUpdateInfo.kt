// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings

import com.intellij.openapi.updateSettings.impl.PluginDownloader

sealed interface PluginUpdateInfo {
  /**
   * A newer compatible version was found. If the repository query also returned errors, those are logged and ignored,
   * since a successful update result takes precedence.
   */
  class UpdateAvailable internal constructor(val update: PluginDownloader) : PluginUpdateInfo

  /**
   * No compatible newer version is available.
   */
  class NoUpdate internal constructor() : PluginUpdateInfo

  /**
   * No update was found and the repository query reported errors.
   */
  class CheckFailed internal constructor(
    /**
     * Errors for plugin repositories.
     */
    val errors: Map<String?, Exception>,
  ) : PluginUpdateInfo
}