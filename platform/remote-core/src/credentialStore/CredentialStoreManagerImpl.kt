// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.credentialStore

import com.intellij.openapi.util.SystemInfo

open class CredentialStoreManagerImpl : CredentialStoreManager {
  override fun isSupported(provider: ProviderType): Boolean =
    if (provider === ProviderType.KEYCHAIN) {
      (SystemInfo.isLinux || SystemInfo.isMac) && availableProviders().contains(provider)
    }
    else {
      availableProviders().contains(provider)
    }

  override fun availableProviders(): List<ProviderType> = ProviderType.values().toList()

  override fun defaultProvider(): ProviderType = if (SystemInfo.isWindows) ProviderType.KEEPASS else ProviderType.KEYCHAIN
}