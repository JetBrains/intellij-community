// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.ant.config.impl;

import com.intellij.util.config.AbstractProperty;
import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene Zhuravlev
 */
public abstract class ClassLoaderHolder {
  protected final AbstractProperty.AbstractPropertyContainer myOptions;
  private ClassLoader myLoader;

  public ClassLoaderHolder(AbstractProperty.AbstractPropertyContainer options) {
    myOptions = options;
  }

  public @NotNull ClassLoader getClassloader() {
    if (myLoader == null) {
      myLoader = buildClasspath();
    }
    return myLoader;
  }

  public void updateClasspath() {
    myLoader = null;
  }
    
  protected abstract ClassLoader buildClasspath();
}
