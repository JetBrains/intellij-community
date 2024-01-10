// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io;

import com.github.markusbernhardt.proxy.ProxySearch;
import com.github.markusbernhardt.proxy.selector.misc.BufferedProxySelector;
import com.github.markusbernhardt.proxy.selector.pac.PacProxySelector;
import com.github.markusbernhardt.proxy.selector.pac.UrlPacScriptSource;

import java.net.ProxySelector;

public class IoServiceImpl implements IoService {
  @Override
  public ProxySelector getProxySelector(String pacUrlForUse) {
    ProxySelector newProxySelector;
    if (pacUrlForUse == null) {
      ProxySearch proxySearch = ProxySearch.getDefaultProxySearch();
      // cache 32 urls for up to 10 min
      proxySearch.setPacCacheSettings(32, 10 * 60 * 1000, BufferedProxySelector.CacheScope.CACHE_SCOPE_HOST);
      newProxySelector = proxySearch.getProxySelector();
    }
    else {
      newProxySelector = new PacProxySelector(new UrlPacScriptSource(pacUrlForUse));
    }
    return newProxySelector;
  }
}
