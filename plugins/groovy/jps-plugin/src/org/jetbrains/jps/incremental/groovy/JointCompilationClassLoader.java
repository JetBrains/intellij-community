// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.incremental.groovy;

import com.intellij.util.lang.PathClassLoaderBuilder;
import com.intellij.util.lang.UrlClassLoader;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
final class JointCompilationClassLoader extends UrlClassLoader {
  JointCompilationClassLoader(@NotNull PathClassLoaderBuilder builder) {
    super(builder);
  }

  @Override
  protected Class<?> _defineClass(String name, byte[] b) {
    try {
      return super._defineClass(name, b);
    }
    catch (NoClassDefFoundError e) {
      NoClassDefFoundError wrap = new NoClassDefFoundError(e.getMessage() + " needed for " + name);
      wrap.initCause(e);
      throw wrap;
    }
  }

  void resetCache() {
    getClassPath().reset(getFiles());
  }
}
