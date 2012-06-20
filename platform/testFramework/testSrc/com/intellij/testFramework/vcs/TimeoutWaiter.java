/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.testFramework.vcs;

import com.intellij.openapi.util.Getter;

public class TimeoutWaiter {
  private Getter<Boolean> myControlled;
  private final static long ourTimeout = 5000;
  private final Object myLock;
  private boolean myExitedByTimeout;

  public TimeoutWaiter() {
    myLock = new Object();
  }

  public void setControlled(final Getter<Boolean> controlled) {
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
