// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.example.settingsowner

import com.intellij.platform.settings.v2.PlatformKeyDeclarer
import com.intellij.platform.settings.v2.SettingsKey

/**
 * Declares a settings key, so a test can load this class through a plugin class loader and see which plugin the
 * key takes.
 *
 * `SettingsKeyOwnerTest` defines this class again from its bytes in a class loader that claims a plugin.
 */
class KeyDeclarer {
  fun declare(name: String): SettingsKey<String> = SettingsKey(name, default = "d")

  /** Returns [PlatformKeyDeclarer.KEY], so the first use of that key comes from this class. */
  fun usePlatformKey(): SettingsKey<String> = PlatformKeyDeclarer.KEY
}
