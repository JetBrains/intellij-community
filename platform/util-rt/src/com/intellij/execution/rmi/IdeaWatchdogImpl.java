// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.rmi;

import java.util.concurrent.atomic.AtomicBoolean;

final class IdeaWatchdogImpl implements IdeaWatchdog {
  volatile long lastTimePinged = System.currentTimeMillis();
  volatile AtomicBoolean isAlive = new AtomicBoolean(true);

  @Override
  public boolean ping() {
    lastTimePinged = System.currentTimeMillis();
    return isAlive();
  }

  @Override
  public void dieNow(int exitCode) {
    System.exit(exitCode);
  }

  @Override
  public boolean isAlive() {
    boolean pingedRecently = System.currentTimeMillis() - lastTimePinged < WAIT_TIMEOUT;
    if (!isAlive.compareAndSet(true, pingedRecently)) {
      return false;
    }
    return pingedRecently;
  }
}
