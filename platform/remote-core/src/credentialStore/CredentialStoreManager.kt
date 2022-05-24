// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.credentialStore

import com.intellij.openapi.application.ApplicationManager

/**
 * Provides information about credential stores in the current environment.
 */
interface CredentialStoreManager {
  companion object {
    fun getInstance(): CredentialStoreManager = ApplicationManager.getApplication().getService(CredentialStoreManager::class.java)
  }

  /**
   * Checks if specified credential store supported in this environment or not
   */
  fun isSupported(provider: ProviderType): Boolean

  /**
   * Lists available in the current environment credential store providers. For example, in headless linux it is difficult or even impossible
   * to properly configure usage of gnome-keychain because through libsecret it requires X11. So it will be better to exclude KEYCHAIN type
   * from this list.
   *
   * @return list of supported providers
   */
  fun availableProviders(): List<ProviderType>

  /**
   * Returns default credential store provider. Most of the time it will be KEYCHAIN but for headless linux environments, for example,
   * it could be changed.
   *
   * @return default credential store provider
   */
  fun defaultProvider(): ProviderType
}
