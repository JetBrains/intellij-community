// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.net

import com.intellij.credentialStore.Credentials
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.use
import java.net.Authenticator
import java.net.ProxySelector
import kotlin.test.assertEquals

internal fun <T> withCustomProxySelector(proxySelector: ProxySelector, body: () -> T): T {
  JdkProxyCustomizer.getInstance().customizeProxySelector(proxySelector).use {
    return body()
  }
}

internal fun <T> withProxySettingsOverride(proxySettingsOverrideProvider: ProxySettingsOverrideProvider, body: () -> T): T {
  Disposer.newDisposable().use { disposable ->
    ApplicationManager.getApplication().extensionArea
      .getExtensionPoint<ProxySettingsOverrideProvider>("com.intellij.proxySettingsOverrideProvider")
      .registerExtension(proxySettingsOverrideProvider, disposable)
    return body()
  }
}

internal fun <T> withProxyConfiguration(proxyConf: ProxyConfiguration, body: () -> T): T {
  val previous = ProxySettings.getInstance().getProxyConfiguration()
  ProxySettings.getInstance().setProxyConfiguration(proxyConf)
  assertEquals(ProxySettings.getInstance().getProxyConfiguration(), proxyConf)
  try {
    return body()
  }
  finally {
    ProxySettings.getInstance().setProxyConfiguration(previous)
  }
}

internal fun <T> withKnownProxyCredentials(host: String, port: Int, credentials: Credentials?, body: () -> T): T {
  val store = ProxyCredentialStore.getInstance()
  val previousCreds = store.getCredentials(host, port)
  val previousRemembered = store.areCredentialsRemembered(host, port)
  store.setCredentials(host, port, credentials, false)
  assertEquals(store.getCredentials(host, port), credentials)
  try {
    return body()
  } finally {
    store.setCredentials(host, port, previousCreds, previousRemembered)
  }
}

internal fun createResetPlatformAuthenticator(): Authenticator =
  IdeProxyAuthenticator(PlatformProxyAuthentication(ProxyCredentialStore::getInstance, DisabledProxyAuthPromptsManager::getInstance))
