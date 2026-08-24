// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.net;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.NTCredentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.config.AuthSchemes;
import org.apache.http.client.config.RequestConfig;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class IdeHttpClientHelpers {
  private IdeHttpClientHelpers() { }

  private static @NotNull ProxyConfiguration getProxyConfiguration() {
    return ProxySettings.getInstance().getProxyConfiguration();
  }

  private static boolean isHttpProxyEnabled() {
    return getProxyConfiguration() instanceof ProxyConfiguration.StaticProxyConfiguration;
  }

  private static boolean isProxyAuthenticationEnabled() {
    return ProxyCredentialStore.getInstance().getCredentials(getProxyConfiguration()) != null;
  }

  private static @NotNull String getProxyHost() {
    return getProxyConfiguration() instanceof ProxyConfiguration.StaticProxyConfiguration http ? http.getHost() : "";
  }

  private static int getProxyPort() {
    return getProxyConfiguration() instanceof ProxyConfiguration.StaticProxyConfiguration http ? http.getPort() : 0;
  }

  /// @deprecated use [PlatformHttpClient] instead
  @Deprecated
  @SuppressWarnings("DeprecatedIsStillUsed")
  public static final class ApacheHttpClient4 {
    /**
     * Install headers for IDE-wide proxy if usage of proxy was enabled in {@link ProxySettings#getProxyConfiguration()}.
     *
     * @param builder HttpClient's request builder used to configure a new client
     * @see #setProxyForUrlIfEnabled(RequestConfig.Builder, String)
     */
    public static void setProxyIfEnabled(@NotNull RequestConfig.Builder builder) {
      if (isHttpProxyEnabled()) {
        builder.setProxy(new HttpHost(getProxyHost(), getProxyPort()));
      }
    }

    /**
     * Install credentials for IDE-wide proxy if usage of proxy and proxy authentication were enabled in {@link ProxySettings#getProxyConfiguration()}.
     *
     * @param provider HttpClient's credentials provider used to configure new client
     * @see #setProxyCredentialsForUrlIfEnabled(CredentialsProvider, String)
     */
    public static void setProxyCredentialsIfEnabled(@NotNull CredentialsProvider provider) {
      if (isHttpProxyEnabled() && isProxyAuthenticationEnabled()) {
        var credentials = ProxyCredentialStore.getInstance().getCredentials(getProxyConfiguration());
        var proxyLogin = StringUtil.notNullize(credentials == null ? null : credentials.getUserName());
        var proxyPassword = StringUtil.notNullize(credentials == null ? null : credentials.getPasswordAsString());
        var ntlmUserPassword = proxyLogin.replace('\\', '/') + ":" + proxyPassword;
        provider.setCredentials(
          new AuthScope(getProxyHost(), getProxyPort(), AuthScope.ANY_REALM, AuthSchemes.NTLM),
          new NTCredentials(ntlmUserPassword)
        );
        provider.setCredentials(
          new AuthScope(getProxyHost(), getProxyPort()),
          new UsernamePasswordCredentials(proxyLogin, proxyPassword)
        );
      }
    }

    /**
     * Install headers for IDE-wide proxy if usage of proxy was enabled AND host of the given url was not added to exclude list
     * in {@link ProxySettings#getProxyConfiguration()}.
     *
     * @param builder HttpClient's request builder used to configure new client
     * @param url     URL to access (only host part is checked)
     */
    public static void setProxyForUrlIfEnabled(@NotNull RequestConfig.Builder builder, @Nullable String url) {
      if (isHttpProxyEnabledForUrl(url)) {
        setProxyIfEnabled(builder);
      }
    }

    /**
     * Install credentials for IDE-wide proxy if usage of proxy was enabled AND host of the given url was not added to exclude list
     * in {@link ProxySettings#getProxyConfiguration()}.
     *
     * @param provider HttpClient's credentials provider used to configure new client
     * @param url      URL to access (only host part is checked)
     */
    public static void setProxyCredentialsForUrlIfEnabled(@NotNull CredentialsProvider provider, @Nullable String url) {
      if (isHttpProxyEnabledForUrl(url)) {
        setProxyCredentialsIfEnabled(provider);
      }
    }

    private static boolean isHttpProxyEnabledForUrl(@Nullable String url) {
      var uri = url != null ? VfsUtil.toUri(url) : null;
      var host = uri != null ? uri.getHost() : null;
      return host == null || host.isBlank() || (
        getProxyConfiguration() instanceof ProxyConfiguration.StaticProxyConfiguration http &&
        !ProxyConfiguration.buildProxyExceptionsMatcher(http.getExceptions()).test(host)
      );
    }
  }
}
