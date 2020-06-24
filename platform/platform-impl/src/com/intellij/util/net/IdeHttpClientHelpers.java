// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.net;

import com.intellij.openapi.util.text.StringUtil;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.NTCredentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.config.AuthSchemes;
import org.apache.http.client.config.RequestConfig;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Mikhail Golubev
 */
public final class IdeHttpClientHelpers {
  private IdeHttpClientHelpers() {
  }

  @NotNull
  private static HttpConfigurable getHttpConfigurable() {
    return HttpConfigurable.getInstance();
  }

  private static boolean isHttpProxyEnabled() {
    return getHttpConfigurable().USE_HTTP_PROXY;
  }

  private static boolean isProxyAuthenticationEnabled() {
    return getHttpConfigurable().PROXY_AUTHENTICATION;
  }

  @NotNull
  private static String getProxyHost() {
    return StringUtil.notNullize(getHttpConfigurable().PROXY_HOST);
  }

  private static int getProxyPort() {
    return getHttpConfigurable().PROXY_PORT;
  }

  @NotNull
  private static String getProxyLogin() {
    return StringUtil.notNullize(getHttpConfigurable().getProxyLogin());
  }

  @NotNull
  private static String getProxyPassword() {
    return StringUtil.notNullize(getHttpConfigurable().getPlainProxyPassword());
  }

  public static final class ApacheHttpClient4 {

    /**
     * Install headers for IDE-wide proxy if usage of proxy was enabled in {@link HttpConfigurable}.
     *
     * @param builder HttpClient's request builder used to configure new client
     * @see #setProxyForUrlIfEnabled(RequestConfig.Builder, String)
     */
    public static void setProxyIfEnabled(@NotNull RequestConfig.Builder builder) {
      if (isHttpProxyEnabled()) {
        builder.setProxy(new HttpHost(getProxyHost(), getProxyPort()));
      }
    }

    /**
     * Install credentials for IDE-wide proxy if usage of proxy and proxy authentication were enabled in {@link HttpConfigurable}.
     *
     * @param provider HttpClient's credentials provider used to configure new client
     * @see #setProxyCredentialsForUrlIfEnabled(CredentialsProvider, String)
     */
    public static void setProxyCredentialsIfEnabled(@NotNull CredentialsProvider provider) {
      if (isHttpProxyEnabled() && isProxyAuthenticationEnabled()) {
        final String ntlmUserPassword = getProxyLogin().replace('\\', '/') + ":" + getProxyPassword();
        provider.setCredentials(new AuthScope(getProxyHost(), getProxyPort(), AuthScope.ANY_REALM, AuthSchemes.NTLM),
                                new NTCredentials(ntlmUserPassword));
        provider.setCredentials(new AuthScope(getProxyHost(), getProxyPort()),
                                new UsernamePasswordCredentials(getProxyLogin(), getProxyPassword()));
      }
    }

    /**
     * Install headers for IDE-wide proxy if usage of proxy was enabled AND host of the given url was not added to exclude list
     * in {@link HttpConfigurable}.
     *
     * @param builder HttpClient's request builder used to configure new client
     * @param url     URL to access (only host part is checked)
     */
    public static void setProxyForUrlIfEnabled(@NotNull RequestConfig.Builder builder, @Nullable String url) {
      if (getHttpConfigurable().isHttpProxyEnabledForUrl(url)) {
        setProxyIfEnabled(builder);
      }
    }

    /**
     * Install credentials for IDE-wide proxy if usage of proxy was enabled AND host of the given url was not added to exclude list
     * in {@link HttpConfigurable}.
     *
     * @param provider HttpClient's credentials provider used to configure new client
     * @param url      URL to access (only host part is checked)
     */
    public static void setProxyCredentialsForUrlIfEnabled(@NotNull CredentialsProvider provider, @Nullable String url) {
      if (getHttpConfigurable().isHttpProxyEnabledForUrl(url)) {
        setProxyCredentialsIfEnabled(provider);
      }
    }
  }
}
