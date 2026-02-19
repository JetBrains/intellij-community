// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.net.internal

import com.intellij.openapi.application.InitialConfigImportState
import com.intellij.openapi.options.ConfigurableUi
import com.intellij.util.net.DisabledProxyAuthPromptsManager
import com.intellij.util.net.ProxyCredentialStore
import com.intellij.util.net.ProxySettings
import com.intellij.util.net.ProxySettingsUi

/**
 * This is a **temporary** hack for switching the default in HttpConfigurable. Do not use.
 * It will be removed once HttpConfigurable is deprecated and migration to a new API for proxy settings is made.
 */
internal class ProxyMigrationServiceImpl : ProxyMigrationService {
  override fun isNewUser(): Boolean = InitialConfigImportState.isNewUser()

  override fun createProxySettingsUi(
    proxySettings: ProxySettings,
    credentialStore: ProxyCredentialStore,
    disabledProxyAuthPromptsManager: DisabledProxyAuthPromptsManager
  ): ConfigurableUi<ProxySettings> = ProxySettingsUi(proxySettings, credentialStore, disabledProxyAuthPromptsManager)
}