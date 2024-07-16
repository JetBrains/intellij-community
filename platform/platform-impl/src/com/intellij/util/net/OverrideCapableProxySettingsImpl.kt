// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.net

import com.intellij.openapi.Disposable

internal class OverrideCapableProxySettingsImpl : OverrideCapableProxySettings(), Disposable {
  private val userProxySettings: ProxySettings = ProxySettingsCompatibilityImpl()

  override val originalProxySettings: ProxySettings get() = userProxySettings

  private fun findOverrideProvider(): ProxySettingsOverrideProvider? {
    return ProxySettingsOverrideProvider.EP_NAME.extensionList.firstOrNull { it.shouldUserSettingsBeOverriden }
  }

  @Volatile
  private var _overrideProvider: ProxySettingsOverrideProvider? = findOverrideProvider()

  init {
    ProxySettingsOverrideProvider.EP_NAME.addChangeListener(
      { _overrideProvider = findOverrideProvider() },
      this
    )
  }

  override var isOverrideEnabled: Boolean
    get() = ProxyOverrideSettings.isOverrideEnabled
    set(value) { ProxyOverrideSettings.isOverrideEnabled = value }

  override val overrideProvider: ProxySettingsOverrideProvider? get() = _overrideProvider

  override fun dispose() {}
}