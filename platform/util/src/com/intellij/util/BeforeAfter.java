// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

public final class BeforeAfter<T> {
  private final T myBefore;
  private final T myAfter;

  public BeforeAfter(T before, T after) {
    myAfter = after;
    myBefore = before;
  }

  public T getAfter() {
    return myAfter;
  }

  public T getBefore() {
    return myBefore;
  }
}
