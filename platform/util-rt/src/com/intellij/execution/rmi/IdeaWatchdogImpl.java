// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.rmi;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.annotations.VisibleForTesting;

import java.util.concurrent.atomic.AtomicBoolean;

@ApiStatus.Internal
@VisibleForTesting
public final class IdeaWatchdogImpl implements IdeaWatchdog {
  private static final long DEFAULT_WAIT_TIMEOUT_MILLIS = 20 * 1000L;
  private static final long DEFAULT_PULSE_TIMEOUT_MILLIS = 9 * 1000L;

  private final long myWaitTimeoutMillis;
  private final long myPulseTimeoutMillis;
  private final AtomicBoolean isAlive = new AtomicBoolean(true);
  private volatile long lastTimePinged = System.currentTimeMillis();

  @VisibleForTesting
  public IdeaWatchdogImpl() {
    this(DEFAULT_WAIT_TIMEOUT_MILLIS, DEFAULT_PULSE_TIMEOUT_MILLIS);
  }

  @VisibleForTesting
  public IdeaWatchdogImpl(long waitTimeoutMillis, long pulseTimeoutMillis) {
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
