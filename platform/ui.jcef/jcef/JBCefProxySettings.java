// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.jcef;

import com.intellij.credentialStore.Credentials;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.util.net.ProxyConfiguration;
import com.intellij.util.net.ProxyCredentialStore;
import com.intellij.util.net.ProxySettings;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

/// Decorating proxy settings for using in tests.
@ApiStatus.Internal
public final class JBCefProxySettings {
  public final @NotNull ProxyConfiguration configuration;
  public final @Nullable Credentials credentials;

  private static @Nullable JBCefProxySettings ourTestInstance;

  private JBCefProxySettings(@NotNull ProxyConfiguration configuration, @Nullable Credentials credentials) {
    this.configuration = configuration;
    this.credentials = credentials;
  }

  public static @NotNull JBCefProxySettings getInstance() {
    if (ApplicationManager.getApplication().isUnitTestMode() && ourTestInstance != null) {
      return ourTestInstance;
    }
    var configuration = ProxySettings.getInstance().getProxyConfiguration();
    return new JBCefProxySettings(configuration, ProxyCredentialStore.getInstance().getCredentials(configuration));
  }

  @TestOnly
  public static void setTestInstance(@NotNull ProxyConfiguration proxyConfiguration, @Nullable Credentials credentials) {
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      throw new IllegalStateException("not in unit test mode!");
    }
    ourTestInstance = new JBCefProxySettings(proxyConfiguration, credentials);
  }
}
