// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.rmi;

import org.jetbrains.annotations.TestOnly;

import java.util.concurrent.atomic.AtomicBoolean;

final class IdeaWatchdogImpl implements IdeaWatchdog {
  private static final long DEFAULT_WAIT_TIMEOUT_MILLIS = 20 * 1000L;
  private static final long DEFAULT_PULSE_TIMEOUT_MILLIS = 9 * 1000L;

  private final long myWaitTimeoutMillis;
  private final long myPulseTimeoutMillis;
  private final AtomicBoolean isAlive = new AtomicBoolean(true);
  private volatile long lastTimePinged = System.currentTimeMillis();

  IdeaWatchdogImpl() {
    this(DEFAULT_WAIT_TIMEOUT_MILLIS, DEFAULT_PULSE_TIMEOUT_MILLIS);
  }

  IdeaWatchdogImpl(long waitTimeoutMillis, long pulseTimeoutMillis) {
    myWaitTimeoutMillis = waitTimeoutMillis;
    myPulseTimeoutMillis = pulseTimeoutMillis;
  }

  @Override
  public boolean ping() {
    lastTimePinged = System.currentTimeMillis();
    return isAlive();
  }

  @Override
  @TestOnly
  public void dieNowTestOnly(int exitCode) {
    System.exit(exitCode);
  }

  @Override
  public boolean die() {
    return isAlive.compareAndSet(true, false);
  }

  @Override
  public boolean isAlive() {
    boolean pingedRecently = System.currentTimeMillis() - lastTimePinged < myWaitTimeoutMillis;
    if (!isAlive.compareAndSet(true, pingedRecently)) {
      return false;
    }
    return pingedRecently;
  }

  @Override
  public long getWaitTimeoutMillis() {
    return myWaitTimeoutMillis;
  }

  @Override
  public long getPulseTimeoutMillis() {
    return myPulseTimeoutMillis;
  }
}
