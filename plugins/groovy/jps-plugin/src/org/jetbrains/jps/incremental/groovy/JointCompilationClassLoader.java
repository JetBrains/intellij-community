// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.incremental.groovy;

import com.intellij.util.lang.Loader;
import com.intellij.util.lang.UrlClassLoader;
import org.jetbrains.annotations.NotNull;

import java.nio.ByteBuffer;

/**
 * @author peter
 */
final class JointCompilationClassLoader extends UrlClassLoader {
  private static final boolean isParallelCapable = registerAsParallelCapable();

  JointCompilationClassLoader(@NotNull UrlClassLoader.Builder builder) {
    super(builder, isParallelCapable);
  }

  @Override
  public Class<?> consumeClassData(@NotNull String name, byte[] data, Loader loader) {
    try {
      return super.consumeClassData(name, data, loader);
    }
    catch (Exception e) {
      NoClassDefFoundError wrap = new NoClassDefFoundError(e.getMessage() + " needed for " + name);
      wrap.initCause(e);
      throw wrap;
    }
  }

  @Override
  public Class<?> consumeClassData(@NotNull String name, ByteBuffer data, Loader loader) {
    try {
      return super.consumeClassData(name, data, loader);
    }
    catch (Exception e) {
      NoClassDefFoundError wrap = new NoClassDefFoundError(e.getMessage() + " needed for " + name);
      wrap.initCause(e);
      throw wrap;
    }
  }

  void resetCache() {
    getClassPath().reset(getFiles());
  }
}
