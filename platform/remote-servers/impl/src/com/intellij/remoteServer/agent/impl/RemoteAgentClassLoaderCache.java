// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remoteServer.agent.impl;

import com.intellij.openapi.diagnostic.Logger;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class RemoteAgentClassLoaderCache {
  private static final Logger LOG = Logger.getInstance(RemoteAgentClassLoaderCache.class);

  private final Map<Set<URL>, URLClassLoader> myUrls2ClassLoader = new HashMap<>();

  public URLClassLoader getOrCreateClassLoader(Set<URL> libraryUrls) {
    URLClassLoader result = myUrls2ClassLoader.get(libraryUrls);
    if (result == null) {
      result = createClassLoaderWithoutApplicationParent(libraryUrls);
      myUrls2ClassLoader.put(libraryUrls, result);
    }
    return result;
  }

  public static URLClassLoader createClassLoaderWithoutApplicationParent(Set<URL> libraryUrls) {
    ClassLoader platformOrBootstrap = ClassLoader.getPlatformClassLoader();
    LOG.info("platform class loader: " + platformOrBootstrap);
    return new URLClassLoader(libraryUrls.toArray(new URL[0]), platformOrBootstrap);
  }
}
