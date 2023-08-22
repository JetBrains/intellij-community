// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util.io;

/**
 * @deprecated use {@link com.intellij.filename.UniqueNameBuilder} instead
 */
@Deprecated
public final class UniqueNameBuilder<T> {
  private final com.intellij.filename.UniqueNameBuilder<T> myDelegate;

  public UniqueNameBuilder(String root, String separator, int maxLength) {
    myDelegate = new com.intellij.filename.UniqueNameBuilder<T>(root, separator);
  }

  public void addPath(T key, String path) {
    myDelegate.addPath(key, path);
  }

  public String getShortPath(T key) {
    return myDelegate.getShortPath(key);
  }
}
