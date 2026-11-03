// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2.impl

// @spec ../../../../../../../../spec/layer-loading.spec.md

import com.intellij.ide.ApplicationInitializedListener
import com.intellij.openapi.components.serviceAsync
import com.intellij.platform.settings.v2.SettingsVault
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting

/**
 * Creates the vault at the application load. So the vault init reloads the application scope at startup in every
 * product mode, also with no open project. See ADR 0116.
 *
 * The platform runs the starter, and so opens a project, only after this listener ends. The start waits only for the
 * creation, because the vault init runs the reload in the background. The preloaded services and the other initialized
 * listeners run at the same time as this listener. When one of them uses settings v2, it creates the vault first, and
 * the vault logs an error. See [SettingsVaultStartCheck].
 *
 * The test application also runs this listener, in `preloadServicesAndCallAppInitializedListeners` of
 * `community/platform/testFramework/common/src/common/testApplication.kt`.
 *
 * Registered as an application initialized listener in `intellij.platform.settings.v2.shared.xml`.
 */
internal class SettingsVaultInitializer : ApplicationInitializedListener {
  override suspend fun execute() {
    SettingsVaultStartCheck.isInitializerStarted = true
    serviceAsync<SettingsVault>()
  }
}

/**
 * Holds the state of the check that [SettingsVaultInitializer] creates the vault first. [SettingsVaultImpl] runs the
 * check when the vault is created.
 */
@ApiStatus.Internal
object SettingsVaultStartCheck {
  /** Holds whether [SettingsVaultInitializer] started. */
  @Volatile
  internal var isInitializerStarted: Boolean = false

  /**
   * Holds whether something created the vault before [SettingsVaultInitializer] started. [SettingsVaultImpl] sets it,
   * and logs an error. `SettingsVaultStartTest` in `remote-dev/settings/v2/lambdaTests/` checks it on each side.
   */
  @Volatile
  @get:VisibleForTesting
  var vaultCreatedBeforeInitializer: Boolean = false
    internal set
}
