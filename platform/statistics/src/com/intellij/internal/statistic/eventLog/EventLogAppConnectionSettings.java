// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog;

import com.intellij.internal.statistic.eventLog.connection.EventLogConnectionSettings;
import com.intellij.internal.statistic.eventLog.connection.request.StatsProxyInfo;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.util.net.HttpConfigurable;
import com.intellij.util.net.ssl.CertificateManager;
import com.intellij.util.proxy.CommonProxy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.net.ssl.SSLContext;
import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.URL;
import java.util.List;

public class EventLogAppConnectionSettings implements EventLogConnectionSettings {
  private static final StatsProxyInfo NO_PROXY = new StatsProxyInfo(Proxy.NO_PROXY, null);

  @NotNull
  @Override
  public String getUserAgent() {
    Application app = ApplicationManager.getApplication();
    if (app != null && !app.isDisposed()) {
      String productName = ApplicationNamesInfo.getInstance().getFullProductName();
      String version = ApplicationInfo.getInstance().getBuild().asStringWithoutProductCode();
      return productName + '/' + version;
    }
    return "IntelliJ";
  }

  @NotNull
  @Override
  public StatsProxyInfo selectProxy(@NotNull String url) {
    Application app = ApplicationManager.getApplication();
    if (app != null && !app.isDisposed()) {
      Proxy proxy = findProxy(url);
      if (proxy != Proxy.NO_PROXY) {
        return new StatsProxyInfo(proxy, getAuthProvider());
      }
    }
    return NO_PROXY;
  }

  @Nullable
  @Override
  public SSLContext getSSLContext() {
    return CertificateManager.getInstance().getSslContext();
  }

  @Nullable
  private static StatsProxyInfo.StatsProxyAuthProvider getAuthProvider() {
    if (HttpConfigurable.getInstance().PROXY_AUTHENTICATION) {
      return EventLogAppProxyAuth.INSTANCE;
    }
    return null;
  }

  @NotNull
  private static Proxy findProxy(@NotNull String url) {
    try {
      List<Proxy> proxies = CommonProxy.getInstance().select(new URL(url));
      return !proxies.isEmpty() ? proxies.get(0) : Proxy.NO_PROXY;
    }
    catch (MalformedURLException e) {
      // ignore
    }
    return Proxy.NO_PROXY;
  }

  private static class EventLogAppProxyAuth implements StatsProxyInfo.StatsProxyAuthProvider {
    private static final EventLogAppProxyAuth INSTANCE = new EventLogAppProxyAuth();

    @Override
    public @Nullable String getProxyLogin() {
      return HttpConfigurable.getInstance().getProxyLogin();
    }

    @Override
    public @Nullable String getProxyPassword() {
      return HttpConfigurable.getInstance().getPlainProxyPassword();
    }
  }
}
