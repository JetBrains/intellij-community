// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.ant.config.impl;

import com.intellij.openapi.progress.ProgressManager;
import com.intellij.util.lang.UrlClassLoader;

import java.net.URL;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author Eugene Zhuravlev
*/
public final class AntResourcesClassLoader extends UrlClassLoader {
  static { if (registerAsParallelCapable()) markParallelCapable(AntResourcesClassLoader.class); }

  private final Set<String> myMisses = new HashSet<>();

  public AntResourcesClassLoader(final List<URL> urls, final ClassLoader parentLoader, final boolean canLockJars, final boolean canUseCache) {
    super(build().urls(urls).parent(parentLoader).allowLock(canLockJars).useCache(canUseCache).noPreload());
  }

  @Override
  protected Class<?> loadClass(final String name, final boolean resolve) throws ClassNotFoundException {
    synchronized (getClassLoadingLock(name)) {
      if (myMisses.contains(name)) {
        throw new ClassNotFoundException(name) {
          @Override
          public synchronized Throwable fillInStackTrace() {
            return this;
          }
        };
      }
      return super.loadClass(name, resolve);
    }
  }

  @Override
  protected Class<?> findClass(String name) throws ClassNotFoundException {
    ProgressManager.checkCanceled();
    try {
      return super.findClass(name);
    }
    catch (ClassNotFoundException e) {
      myMisses.add(name);
      throw e;
    }
  }
}