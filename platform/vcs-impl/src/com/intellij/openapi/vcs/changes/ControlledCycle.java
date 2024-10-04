// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.util.Alarm;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

@ApiStatus.Internal
public final class ControlledCycle {
  private static final Logger LOG = Logger.getInstance(ControlledCycle.class);

  private final Alarm mySimpleAlarm;
  private final int myRefreshInterval;
  private final Runnable myRunnable;

  private final AtomicBoolean myActive;

  public ControlledCycle(@NotNull Project project,
                         final Supplier<Boolean> callback,
                         @NotNull final String name,
                         final int refreshInterval) {
    myRefreshInterval = refreshInterval;
    myActive = new AtomicBoolean(false);
    myRunnable = new Runnable() {
      boolean shouldBeContinued = true;

      @Override
      public void run() {
        if (!myActive.get() || project.isDisposed()) return;
        try {
          shouldBeContinued = callback.get();
        }
        catch (ProcessCanceledException e) {
          return;
        }
        catch (RuntimeException e) {
          LOG.info(e);
        }
        if (!shouldBeContinued) {
          myActive.set(false);
        }
        else {
          mySimpleAlarm.addRequest(myRunnable, myRefreshInterval);
        }
      }
    };
    mySimpleAlarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, project);
  }

  public void startIfNotStarted() {
    if (myActive.compareAndSet(false, true)) {
      mySimpleAlarm.addRequest(myRunnable, myRefreshInterval);
    }
  }

  public void stop() {
    myActive.set(false);
  }
}
