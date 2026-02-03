// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.lang;

import com.intellij.util.lang.JarLoader.Attribute;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class CachePoolImpl implements UrlClassLoader.CachePool {
  final Map<Path, ClasspathCache.IndexRegistrar> loaderIndexCache = new ConcurrentHashMap<>();
  private final Map<Path, Map<Attribute, String>> manifestData = new ConcurrentHashMap<>();

  Map<Attribute, String> getManifestData(@NotNull Path file) {
    return manifestData.get(file);
  }

  void cacheManifestData(@NotNull Path file, @NotNull Map<Attribute, String> manifestAttributes) {
    manifestData.put(file, manifestAttributes);
  }
}
