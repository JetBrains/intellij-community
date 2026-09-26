// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog;

import com.intellij.ide.ui.IdeUiService;
import com.intellij.internal.statistic.eventLog.connection.metadata.StatsConnectionSettings;
import com.intellij.internal.statistic.eventLog.connection.metadata.StatsProxyInfo;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.net.ssl.SSLContext;
import java.net.Proxy;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;

@ApiStatus.Internal
public final class StatsAppConnectionSettings implements StatsConnectionSettings {
  private static final StatsProxyInfo NO_PROXY = new StatsProxyInfo(Proxy.NO_PROXY, null);

  @Override
  public @NotNull String provideUserAgent() {
    var app = ApplicationManager.getApplication();
    if (app != null && !app.isDisposed()) {
      var productName = ApplicationNamesInfo.getInstance().getFullProductName();
      var version = ApplicationInfo.getInstance().getBuild().asStringWithoutProductCode();
      return productName + '/' + version;
    }
    return "IntelliJ";
  }

  @Override
  public @NotNull StatsProxyInfo provideProxy(@NotNull String url) {
    var app = ApplicationManager.getApplication();
    if (app != null && !app.isDisposed()) {
      var proxy = findProxy(url);
      if (proxy != null) {
        return new StatsProxyInfo(proxy, getAuthProvider());
      }
    }
    return NO_PROXY;
  }

  @Override
  public @Nullable SSLContext provideSSLContext() {
    return IdeUiService.getInstance().getSslContext();
  }

  @Override
  public @NotNull Map<String, String> provideExtraHeaders() {
    var externalEventLogSettings = StatisticsEventLogProviderUtil.getExternalEventLogSettings();
    return externalEventLogSettings != null ? externalEventLogSettings.getExtraLogUploadHeaders() : Map.of();
  }

  private static @Nullable Proxy findProxy(String url) {
    try {
      var proxies = IdeUiService.getInstance().getProxyList(new URI(url));
      if (!proxies.isEmpty()) {
        return proxies.getFirst();
      }
    }
    catch (URISyntaxException _) { }
    return null;
  }

  private static @Nullable StatsProxyInfo.StatsProxyAuthProvider getAuthProvider() {
    var credentials = IdeUiService.getInstance().getProxyCredentials();
    return credentials == null ? null : new StatsProxyInfo.StatsProxyAuthProvider() {
      @Override public @Nullable String getProxyLogin() { return credentials.first; }
      @Override public @Nullable String getProxyPassword() { return credentials.second != null ? new String(credentials.second) : null; }
    };
  }
}
