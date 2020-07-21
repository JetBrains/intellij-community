// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.util.proxy.CommonProxy;
import org.jetbrains.annotations.NotNull;

import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.URL;
import java.util.List;

public class EventLogAppConnectionSettings implements EventLogConnectionSettings {

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
  public Proxy selectProxy(@NotNull String url) {
    Application app = ApplicationManager.getApplication();
    if (app != null && !app.isDisposed()) {
      try {
        List<Proxy> proxies = CommonProxy.getInstance().select(new URL(url));
        return !proxies.isEmpty() ? proxies.get(0) : Proxy.NO_PROXY;
      }
      catch (MalformedURLException e) {
        // ignore
      }
    }
    return Proxy.NO_PROXY;
  }
}
