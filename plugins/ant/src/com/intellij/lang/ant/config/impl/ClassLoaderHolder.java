/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.lang.ant.config.impl;

import com.intellij.util.config.AbstractProperty;
import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene Zhuravlev
 *         Date: Mar 26, 2007
 */
public abstract class ClassLoaderHolder {
  protected final AbstractProperty.AbstractPropertyContainer myOptions;
  private ClassLoader myLoader;

  public ClassLoaderHolder(AbstractProperty.AbstractPropertyContainer options) {
    myOptions = options;
  }

  @NotNull
  public ClassLoader getClassloader() {
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
