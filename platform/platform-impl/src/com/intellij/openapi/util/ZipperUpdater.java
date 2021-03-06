// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.progress.SomeQueue;
import com.intellij.openapi.progress.util.BackgroundTaskUtil;
import com.intellij.util.Alarm;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@SomeQueue
public final class ZipperUpdater {
  private final Alarm myAlarm;
  private boolean myRaised;
  private final Object myLock = new Object();
  private final int myDelay;
  private final Alarm.ThreadToUse myThreadToUse;

  public ZipperUpdater(final int delay, @NotNull Disposable parentDisposable) {
    this(delay, Alarm.ThreadToUse.POOLED_THREAD, parentDisposable);
  }

  public ZipperUpdater(final int delay, final Alarm.ThreadToUse threadToUse, @NotNull Disposable parentDisposable) {
    myDelay = delay;
    myThreadToUse = threadToUse;
    myAlarm = new Alarm(threadToUse, parentDisposable);
  }

  public void queue(@NotNull final Runnable runnable) {
    queue(runnable, false, false);
  }

  public void queue(@NotNull final Runnable runnable, final boolean urgent, final boolean anyModality) {
    synchronized (myLock) {
      if (myAlarm.isDisposed()) return;
      final boolean wasRaised = myRaised;
      myRaised = true;
      if (!wasRaised) {
        final Runnable request = new Runnable() {
          @Override
          public void run() {
            synchronized (myLock) {
              if (!myRaised) return;
              myRaised = false;
            }
            BackgroundTaskUtil.runUnderDisposeAwareIndicator(myAlarm, runnable);
          }

          @Override
          public String toString() {
            return runnable.toString();
          }
        };

        addRequest(request, urgent, anyModality);
      }
    }
  }

  private void addRequest(@NotNull Runnable request, boolean urgent, boolean anyModality) {
    int delay = urgent ? 0 : myDelay;

    if (Alarm.ThreadToUse.SWING_THREAD.equals(myThreadToUse)) {
      if (anyModality) {
        myAlarm.addRequest(request, delay, ModalityState.any());
      }
      else if (!ApplicationManager.getApplication().isDispatchThread()) {
        myAlarm.addRequest(request, delay, ModalityState.NON_MODAL);
      }
      else {
        myAlarm.addRequest(request, delay);
      }
    }
    else {
      myAlarm.addRequest(request, delay);
    }
  }

  public void stop() {
    myAlarm.cancelAllRequests();
  }

  @TestOnly
  public void waitForAllExecuted(long timeout, @NotNull TimeUnit unit) {
    try {
      myAlarm.waitForAllExecuted(timeout, unit);
    }
    catch (InterruptedException | ExecutionException | TimeoutException e) {
      throw new RuntimeException(e);
    }
  }
}
