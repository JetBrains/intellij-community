// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util;

public final class Timed<T> {
  private final T myT;
  private final long myTime;

  public Timed(final T t, final long time) {
    myT = t;
    myTime = time;
  }

  public Timed(final T t) {
    myT = t;
    myTime = System.currentTimeMillis();
  }

  public T getT() {
    return myT;
  }

  public long getTime() {
    return myTime;
  }
}
