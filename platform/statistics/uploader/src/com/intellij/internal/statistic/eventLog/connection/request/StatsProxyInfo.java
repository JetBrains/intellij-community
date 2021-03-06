// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.connection.request;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.Proxy;

public class StatsProxyInfo {
  private final Proxy myProxy;
  private final StatsProxyAuthProvider myProxyAuth;

  public StatsProxyInfo(@NotNull Proxy proxy, @Nullable StatsProxyAuthProvider proxyAuth) {
    myProxy = proxy;
    myProxyAuth = proxyAuth;
  }

  public boolean isNoProxy() {
    return myProxy == Proxy.NO_PROXY;
  }

  @NotNull
  public Proxy getProxy() {
    return myProxy;
  }

  @Nullable
  public StatsProxyAuthProvider getProxyAuth() {
    return myProxyAuth;
  }

  public interface StatsProxyAuthProvider {
    @Nullable
    String getProxyLogin();

    @Nullable
    String getProxyPassword();
  }
}
