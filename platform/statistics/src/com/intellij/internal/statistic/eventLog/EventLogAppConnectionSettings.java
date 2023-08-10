// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog;

import com.intellij.ide.ui.IdeUiService;
import com.intellij.internal.statistic.eventLog.connection.EventLogConnectionSettings;
import com.intellij.internal.statistic.eventLog.connection.request.StatsProxyInfo;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.net.ssl.SSLContext;
import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class EventLogAppConnectionSettings implements EventLogConnectionSettings {
  private static final StatsProxyInfo NO_PROXY = new StatsProxyInfo(Proxy.NO_PROXY, null);

  @Override
  public @NotNull String getUserAgent() {
    Application app = ApplicationManager.getApplication();
    if (app != null && !app.isDisposed()) {
      String productName = ApplicationNamesInfo.getInstance().getFullProductName();
      String version = ApplicationInfo.getInstance().getBuild().asStringWithoutProductCode();
      return productName + '/' + version;
    }
    return "IntelliJ";
  }

  @Override
  public @NotNull StatsProxyInfo selectProxy(@NotNull String url) {
    Application app = ApplicationManager.getApplication();
    if (app != null && !app.isDisposed()) {
      Proxy proxy = findProxy(url);
      if (proxy != Proxy.NO_PROXY) {
        return new StatsProxyInfo(proxy, getAuthProvider());
      }
    }
    return NO_PROXY;
  }

  @Override
  public @Nullable SSLContext getSSLContext() {
    return IdeUiService.getInstance().getSslContext();
  }

  @Override
  public @NotNull Map<String, String> getExtraHeaders() {
    ExternalEventLogSettings externalEventLogSettings = StatisticsEventLogProviderUtil.getExternalEventLogSettings();
    if (externalEventLogSettings != null) {
      return externalEventLogSettings.getExtraLogUploadHeaders();
    } else {
      return Collections.emptyMap();
    }
  }

  private static @Nullable StatsProxyInfo.StatsProxyAuthProvider getAuthProvider() {
    if (IdeUiService.getInstance().isProxyAuth()) {
      return EventLogAppProxyAuth.INSTANCE;
    }
    return null;
  }

  private static @NotNull Proxy findProxy(@NotNull String url) {
    try {
      List<Proxy> proxies = IdeUiService.getInstance().getProxyList(new URL(url));
      return !proxies.isEmpty() ? proxies.get(0) : Proxy.NO_PROXY;
    }
    catch (MalformedURLException e) {
      // ignore
    }
    return Proxy.NO_PROXY;
  }

  private static final class EventLogAppProxyAuth implements StatsProxyInfo.StatsProxyAuthProvider {
    private static final EventLogAppProxyAuth INSTANCE = new EventLogAppProxyAuth();

    @Override
    public @Nullable String getProxyLogin() {
      return IdeUiService.getInstance().getProxyLogin();
    }

    @Override
    public @Nullable String getProxyPassword() {
      return IdeUiService.getInstance().getPlainProxyPassword();
    }
  }
}
