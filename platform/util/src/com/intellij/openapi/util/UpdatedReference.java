// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util;

public final class UpdatedReference<T> {
  private T myT;
  private long myTime;

  public UpdatedReference(T t) {
    myT = t;
    myTime = System.currentTimeMillis();
  }

  public UpdatedReference(T t, long time) {
    myT = t;
    myTime = time;
  }

  public boolean isTimeToUpdate(final long interval) {
    return (System.currentTimeMillis() - myTime) > interval;
  }

  public void updateT(final T t) {
    myT = t;
    myTime = System.currentTimeMillis();
  }

  public T getT() {
    return myT;
  }

  public void updateTs() {
    myTime = System.currentTimeMillis();
  }

  public long getTime() {
    return myTime;
  }
}
