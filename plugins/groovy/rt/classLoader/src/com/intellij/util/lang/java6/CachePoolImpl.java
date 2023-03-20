// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.lang.java6;

import org.jetbrains.annotations.NotNull;

import java.net.URL;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.Attributes;

final class CachePoolImpl implements UrlClassLoader.CachePool {
  private final Map<URL, ClasspathCache.LoaderData> myLoaderIndexCache = new ConcurrentHashMap<>();
  private final Map<URL, Attributes> myManifestData = new ConcurrentHashMap<>();

  void cacheData(@NotNull URL url, @NotNull ClasspathCache.LoaderData data) {
    myLoaderIndexCache.put(url, data);
  }

  ClasspathCache.LoaderData getCachedData(@NotNull URL url) {
    return myLoaderIndexCache.get(url);
  }

  Attributes getManifestData(@NotNull URL url) {
    return myManifestData.get(url);
  }

  void cacheManifestData(@NotNull URL url, @NotNull Attributes manifestAttributes) {
    myManifestData.put(url, manifestAttributes);
  }
}
