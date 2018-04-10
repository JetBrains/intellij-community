/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.util.lang;

import org.jetbrains.annotations.NotNull;

import java.net.URL;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.Attributes;

/**
 * @author peter
 */
class CachePoolImpl implements UrlClassLoader.CachePool {
  private final Map<URL, ClasspathCache.LoaderData> myLoaderIndexCache = new ConcurrentHashMap<URL, ClasspathCache.LoaderData>();
  
  void cacheData(@NotNull URL url, @NotNull ClasspathCache.LoaderData data) {
    myLoaderIndexCache.put(url, data);
  }

  ClasspathCache.LoaderData getCachedData(@NotNull URL url) {
    return myLoaderIndexCache.get(url);
  }

  private final Map<URL, Attributes> myManifestData = new ConcurrentHashMap<URL, Attributes>();

  Attributes getManifestData(URL url) {
    return myManifestData.get(url);
  }

  void cacheManifestData(URL url, Attributes manifestAttributes) {
    myManifestData.put(url, manifestAttributes);
  }
}
