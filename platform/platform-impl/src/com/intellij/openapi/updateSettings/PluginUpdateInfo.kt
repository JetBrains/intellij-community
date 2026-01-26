// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings

import com.intellij.openapi.updateSettings.impl.PluginDownloader
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
sealed interface PluginUpdateInfo {
  class UpdateAvailable(val update: PluginDownloader) : PluginUpdateInfo

  class NoUpdate : PluginUpdateInfo

  class CheckFailed(
    /**
     * Errors for plugin repositories.
     */
    val errors: Map<String?, Exception>
  ) : PluginUpdateInfo
}