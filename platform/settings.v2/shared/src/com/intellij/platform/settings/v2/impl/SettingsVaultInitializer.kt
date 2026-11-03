// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2.impl

// @spec ../../../../../../../../spec/layer-loading.spec.md

import com.intellij.ide.ApplicationActivity
import com.intellij.openapi.components.serviceAsync
import com.intellij.platform.settings.v2.SettingsVault

/**
 * Creates the vault at the application load, so the vault init reloads the application scope at startup in every
 * product mode, also with no open project. The application start does not wait for it. See ADR 0116.
 *
 * Without it, the platform creates the vault only on its first use. A unit test still creates the vault that way,
 * because the platform runs no application activity in the unit test mode.
 *
 * Registered as an application activity in `intellij.platform.settings.v2.shared.xml`.
 */
internal class SettingsVaultInitializer : ApplicationActivity {
  override suspend fun execute() {
    serviceAsync<SettingsVault>()
  }
}
