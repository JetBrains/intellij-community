// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2.impl

// @spec ../../../../../../../../spec/key-registry.spec.md

import com.intellij.platform.settings.v2.EDIT_TEAM_LAYER
import com.intellij.platform.settings.v2.LANGUAGE
import com.intellij.platform.settings.v2.OS
import com.intellij.platform.settings.v2.PER_OS_SETTINGS
import com.intellij.platform.settings.v2.SettingsKey
import com.intellij.platform.settings.v2.SettingsKeyPromoter

/**
 * Registers the keys that the settings v2 API module declares and the migration ledgers, so a write of them is not a
 * missing promotion.
 * Registered as a `settings.v2.keyPromoter` in `intellij.platform.settings.v2.shared.xml`.
 */
internal class PlatformKeyPromoter : SettingsKeyPromoter {
  override fun promote(register: (SettingsKey<*>) -> Unit) {
    register(EDIT_TEAM_LAYER)
    register(LANGUAGE)
    register(OS)
    register(PER_OS_SETTINGS)
    register(APP_MIGRATION_LEDGER)
    register(PROJECT_MIGRATION_LEDGER)
  }
}
