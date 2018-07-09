/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.lang.ant.config.impl;

import com.intellij.openapi.progress.ProgressManager;
import com.intellij.util.lang.UrlClassLoader;
import gnu.trove.THashSet;

import java.net.URL;
import java.util.List;
import java.util.Set;

/**
 * @author Eugene Zhuravlev
 * @since Oct 21, 2008
*/
public class AntResourcesClassLoader extends UrlClassLoader {
  static { if (registerAsParallelCapable()) markParallelCapable(AntResourcesClassLoader.class); }

  private final Set<String> myMisses = new THashSet<>();

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
  protected Class findClass(final String name) throws ClassNotFoundException {
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