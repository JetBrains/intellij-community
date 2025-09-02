// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.net

import com.intellij.openapi.util.Ref

private class OverrideCapableProxySettingsImpl : OverrideCapableProxySettings() {
  override val originalProxySettings = ProxySettingsCompatibilityImpl()

  override var isOverrideEnabled: Boolean
    get() = ProxyOverrideSettings.isOverrideEnabled
    set(value) {
      ProxyOverrideSettings.isOverrideEnabled = value
    }

  override val overrideProvider: ProxySettingsOverrideProvider?
    get() {
      return ProxySettingsOverrideProvider.EP_NAME.computeIfAbsent(OverrideCapableProxySettingsImpl::class.java) {
        Ref(ProxySettingsOverrideProvider.EP_NAME.lazySequence().firstOrNull { it.shouldUserSettingsBeOverriden })
      }.get()
    }
}