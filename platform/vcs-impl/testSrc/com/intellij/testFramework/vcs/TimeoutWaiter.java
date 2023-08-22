// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework.vcs;

import java.util.function.Supplier;

public final class TimeoutWaiter {
  private Supplier<Boolean> myControlled;
  private final static long ourTimeout = 5000;
  private final Object myLock;
  private boolean myExitedByTimeout;

  public TimeoutWaiter() {
    myLock = new Object();
  }

  public void setControlled(final Supplier<Boolean> controlled) {
    myControlled = controlled;
  }

  public void startTimeout() {
    assert myControlled != null;

    final long start = System.currentTimeMillis();
    synchronized (myLock) {
      while (true) {
        try {
          myLock.wait(300);
        }
        catch (InterruptedException e) {
          //
        }
        if ((System.currentTimeMillis() - start) >= ourTimeout) {
          myExitedByTimeout = true;
          return;
        }
        if (Boolean.TRUE.equals(myControlled.get())) return;
      }
    }
  }

  public boolean isExitedByTimeout() {
    return myExitedByTimeout;
  }
}
