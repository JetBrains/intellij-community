// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2.impl

// @spec ../../../../../../../../spec/snapshot-cache.spec.md

import com.intellij.ide.caches.CachesInvalidator
import com.intellij.openapi.diagnostic.logger
import com.intellij.platform.settings.v2.impl.cache.SettingsCacheStorage
import java.io.IOException

/**
 * Clears the settings snapshot caches on "Invalidate Caches / Restart". It stamps the single shared marker, so the
 * next load ignores every existing cache: the application cache and each project cache. It does not enumerate the
 * caches (see [SettingsCacheStorage]), so it is fast, as the [CachesInvalidator] contract requires. A cache file is
 * deleted on its next load after the restart.
 *
 * Registered as `com.intellij.cachesInvalidator` in `intellij.platform.settings.v2.shared.xml`.
 */
internal class SettingsCacheInvalidator : CachesInvalidator() {
  override fun invalidateCaches() {
    try {
      SettingsCacheStorage.invalidateAll()
    }
    catch (e: IOException) {
      logger<SettingsCacheInvalidator>().warn("Failed to stamp the settings cache invalidation marker", e)
    }
  }
}
