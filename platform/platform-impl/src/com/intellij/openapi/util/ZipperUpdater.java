// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.progress.SomeQueue;
import com.intellij.util.Alarm;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@SomeQueue
public class ZipperUpdater {
  private final Alarm myAlarm;
  private boolean myRaised;
  private final Object myLock = new Object();
  private final int myDelay;
  private final Alarm.ThreadToUse myThreadToUse;
  private boolean myIsEmpty;

  public ZipperUpdater(final int delay, @NotNull Disposable parentDisposable) {
    myDelay = delay;
    myIsEmpty = true;
    myThreadToUse = Alarm.ThreadToUse.POOLED_THREAD;
    myAlarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, parentDisposable);
  }

  public ZipperUpdater(final int delay, final Alarm.ThreadToUse threadToUse, @NotNull Disposable parentDisposable) {
    myDelay = delay;
    myThreadToUse = threadToUse;
    myIsEmpty = true;
    myAlarm = new Alarm(threadToUse, parentDisposable);
  }

  public void queue(@NotNull final Runnable runnable) {
    queue(runnable, false);
  }

  private void queue(@NotNull final Runnable runnable, final boolean urgent) {
    queue(runnable, urgent, false);
  }

  public void queue(@NotNull final Runnable runnable, final boolean urgent, final boolean anyModality) {
    synchronized (myLock) {
      if (myAlarm.isDisposed()) return;
      final boolean wasRaised = myRaised;
      myRaised = true;
      myIsEmpty = false;
      if (! wasRaised) {
        final Runnable request = new Runnable() {
          @Override
          public void run() {
            synchronized (myLock) {
              if (!myRaised) return;
              myRaised = false;
            }
            runnable.run();
            synchronized (myLock) {
              myIsEmpty = !myRaised;
            }
          }

          @Override
          public String toString() {
            return runnable.toString();
          }
        };
        if (Alarm.ThreadToUse.SWING_THREAD.equals(myThreadToUse)) {
          if (anyModality) {
            myAlarm.addRequest(request, urgent ? 0 : myDelay, ModalityState.any());
          } else if (!ApplicationManager.getApplication().isDispatchThread()) {
            myAlarm.addRequest(request, urgent ? 0 : myDelay, ModalityState.NON_MODAL);
          } else {
            myAlarm.addRequest(request, urgent ? 0 : myDelay);
          }
        }
        else {
          myAlarm.addRequest(request, urgent ? 0 : myDelay);
        }
      }
    }
  }

  public boolean isEmpty() {
    synchronized (myLock) {
      return myIsEmpty;
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
