// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io;

import com.github.markusbernhardt.proxy.ProxySearch;
import com.github.markusbernhardt.proxy.selector.misc.BufferedProxySelector;
import com.github.markusbernhardt.proxy.util.ProxyUtil;
import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.net.ProxySelector;

@ApiStatus.Internal
public class IoServiceImpl implements IoService {
  @Override
  public ProxySelector getProxySelector(String pacUrlForUse) {
    ProxySelector newProxySelector;
    if (pacUrlForUse == null) {
      final ProxySearch proxySearch = getDefaultProxySearchStrategy();
      newProxySelector = proxySearch.getProxySelector();
    }
    else {
      newProxySelector = ProxyUtil.buildPacSelectorForUrl(pacUrlForUse);
    }
    return newProxySelector;
  }

  private static @NotNull ProxySearch getDefaultProxySearchStrategy() {
    ProxySearch proxySearch = new ProxySearch();
    proxySearch.addStrategy(ProxySearch.Strategy.JAVA);
    proxySearch.addStrategy(ProxySearch.Strategy.OS_DEFAULT);
    if (SystemInfo.isWindows) { // for some (likely legacy) reasons, system proxy settings can only be detected using the search for IE
      proxySearch.addStrategy(ProxySearch.Strategy.IE);
    }
    proxySearch.addStrategy(ProxySearch.Strategy.ENV_VAR);
    // cache 32 urls for up to 10 min
    proxySearch.setPacCacheSettings(32, 10 * 60 * 1000, BufferedProxySelector.CacheScope.CACHE_SCOPE_HOST);
    return proxySearch;
  }
}
