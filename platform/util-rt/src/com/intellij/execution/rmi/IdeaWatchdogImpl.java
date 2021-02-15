// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.rmi;

class IdeaWatchdogImpl implements IdeaWatchdog {
  volatile long lastTimePinged;
  volatile boolean dead = false;

  @Override
  public void ping() {
    lastTimePinged = System.currentTimeMillis();
  }

  @Override
  public void die() {
    dead = true;
  }

  @Override
  public boolean isAlive() {
    return !dead && System.currentTimeMillis() - lastTimePinged < WAIT_TIMEOUT;
  }
}
