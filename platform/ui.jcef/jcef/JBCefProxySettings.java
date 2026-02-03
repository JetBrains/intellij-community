// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.jcef;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.util.net.HttpConfigurable;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Decorates {@link HttpConfigurable} so that it can be used in tests as well.
 */
@ApiStatus.Internal
public final class JBCefProxySettings {
  public final boolean USE_HTTP_PROXY;
  public final boolean PROXY_TYPE_IS_SOCKS;
  public final boolean USE_PROXY_PAC;
  public final boolean USE_PAC_URL;
  public final @Nullable String PAC_URL;
  public final @Nullable String PROXY_HOST;
  public final int PROXY_PORT;
  public final @Nullable String PROXY_EXCEPTIONS;
  public final boolean PROXY_AUTHENTICATION;
  private final @NotNull Credentials myCredentials;

  private static @Nullable JBCefProxySettings ourTestInstance;

  private JBCefProxySettings(boolean useHttpProxy,
                             boolean proxyTypeIsSocks,
                             boolean useProxyPac,
                             boolean usePacUrl,
                             @Nullable String pacUrl,
                             @Nullable String proxyHost,
                             int proxyPort,
                             @Nullable String proxyExceptions,
                             boolean proxyAuthentication,
                             @NotNull Credentials credentials)
  {
    USE_HTTP_PROXY = useHttpProxy;
    PROXY_TYPE_IS_SOCKS = proxyTypeIsSocks;
    USE_PROXY_PAC = useProxyPac;
    USE_PAC_URL = usePacUrl;
    PAC_URL = pacUrl;
    PROXY_HOST = proxyHost;
    PROXY_PORT = proxyPort;
    PROXY_AUTHENTICATION = proxyAuthentication;
    PROXY_EXCEPTIONS = proxyExceptions;
    myCredentials = credentials;
  }

  public static @NotNull JBCefProxySettings getInstance() {
    if (ApplicationManager.getApplication().isUnitTestMode() && ourTestInstance != null) {
      return ourTestInstance;
    }
    HttpConfigurable httpSettings = HttpConfigurable.getInstance();
    return new JBCefProxySettings(
      httpSettings.USE_HTTP_PROXY,
      httpSettings.PROXY_TYPE_IS_SOCKS,
      httpSettings.USE_PROXY_PAC,
      httpSettings.USE_PAC_URL,
      httpSettings.PAC_URL,
      httpSettings.PROXY_HOST,
      httpSettings.PROXY_PORT,
      httpSettings.PROXY_EXCEPTIONS,
      httpSettings.PROXY_AUTHENTICATION,
      new Credentials() {
        @Override
        public @Nullable String getLogin() {
          return httpSettings.getProxyLogin();
        }

        @Override
        public @Nullable String getPassword() {
          return httpSettings.getPlainProxyPassword();
        }
      });
  }

  public static void setTestInstance(boolean useHttpProxy,
                                     boolean proxyTypeIsSocks,
                                     boolean useProxyPac,
                                     boolean usePacUrl,
                                     @Nullable String pacUrl,
                                     @Nullable String proxyHost,
                                     int proxyPort,
                                     @Nullable String proxyExceptions,
                                     boolean proxyAuthentication,
                                     @Nullable String login,
                                     @Nullable String password)
  {
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      throw new IllegalStateException("not in unit test mode!");
    }
    ourTestInstance = new JBCefProxySettings(
      useHttpProxy,
      proxyTypeIsSocks,
      useProxyPac,
      usePacUrl,
      pacUrl,
      proxyHost,
      proxyPort,
      proxyExceptions,
      proxyAuthentication,
      new Credentials() {
        @Override
        public @Nullable String getLogin() {
          return login;
        }
        @Override
        public @Nullable String getPassword() {
          return password;
        }
      });
  }

  public @Nullable String getProxyLogin() {
    return myCredentials.getLogin();
  }

  public @Nullable String getPlainProxyPassword() {
    return myCredentials.getPassword();
  }

  private interface Credentials {
    @Nullable String getLogin();
    @Nullable String getPassword();
  }
}
