// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.lang;

import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.Attributes;

final class CachePoolImpl implements UrlClassLoader.CachePool {
  private final Map<Path, ClasspathCache.LoaderData> loaderIndexCache = new ConcurrentHashMap<>();
  private final Map<Path, Attributes> manifestData = new ConcurrentHashMap<>();

  void cacheData(@NotNull Path url, @NotNull ClasspathCache.LoaderData data) {
    loaderIndexCache.put(url, data);
  }

  ClasspathCache.LoaderData getCachedData(@NotNull Path file) {
    return loaderIndexCache.get(file);
  }

  Attributes getManifestData(@NotNull Path file) {
    return manifestData.get(file);
  }

  void cacheManifestData(@NotNull Path file, @NotNull Attributes manifestAttributes) {
    manifestData.put(file, manifestAttributes);
  }
}
