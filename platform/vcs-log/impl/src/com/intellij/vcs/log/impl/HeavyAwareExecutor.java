/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.vcs.log.impl;

import com.intellij.concurrency.JobScheduler;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.impl.CoreProgressManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.io.storage.HeavyProcessLatch;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class HeavyAwareExecutor {
  public static void executeOutOfHeavyProcessLater(@NotNull Runnable command, int delayMs) {
    HeavyProcessLatch.INSTANCE.executeOutOfHeavyProcess(() -> JobScheduler.getScheduler().schedule(() -> {
      if (HeavyProcessLatch.INSTANCE.isRunning()) {
        executeOutOfHeavyProcessLater(command, delayMs);
      }
      else {
        command.run();
      }
    }, delayMs, TimeUnit.MILLISECONDS));
  }

  /**
   * Starts a task in background after heavy process is finished after a delay.
   * When a "long" heavy activity is started during task execution, task is cancelled.
   *
   * @param task                   task to execute
   * @param indicator              progress indicator for executing the task
   * @param delayMs                delay in milliseconds to execute the task after a heavy activity is finished
   * @param longActivityDurationMs length of activity in milliseconds that cancels the task
   */
  public static void executeOutOfHeavyProcess(@NotNull Task.Backgroundable task,
                                              @NotNull ProgressIndicator indicator,
                                              int delayMs,
                                              int longActivityDurationMs) {
    HeavyProcessLatch.INSTANCE.executeOutOfHeavyProcess(() -> JobScheduler.getScheduler().schedule(() -> {
      if (HeavyProcessLatch.INSTANCE.isRunning()) {
        executeOutOfHeavyProcess(task, indicator, delayMs, longActivityDurationMs);
      }
      else {
        Disposable disposable = Disposer.newDisposable();
        ((CoreProgressManager)ProgressManager.getInstance()).runProcessWithProgressAsynchronously(task, indicator,
                                                                                                  () -> Disposer.dispose(disposable));
        new CancellingOnHeavyProcessListener(indicator, longActivityDurationMs, disposable);
      }
    }, delayMs, TimeUnit.MILLISECONDS));
  }

  private static class CancellingOnHeavyProcessListener implements HeavyProcessLatch.HeavyProcessListener {
    @NotNull private final ProgressIndicator myIndicator;
    private final int myLongActivityDurationMs;

    @Nullable private ScheduledFuture<?> myFuture = null;

    public CancellingOnHeavyProcessListener(@NotNull ProgressIndicator indicator,
                                            int logActivityDurationMs,
                                            @NotNull Disposable disposable) {
      myIndicator = indicator;
      myLongActivityDurationMs = logActivityDurationMs;

      HeavyProcessLatch.INSTANCE.addListener(this, disposable);

      scheduleCancel(); // in case some sneaky heavy process started before we managed to add a listener
    }

    @Override
    public void processStarted() {
      scheduleCancel();
    }

    @Override
    public void processFinished() {
      doNotCancel();
    }

    private synchronized void scheduleCancel() {
      if (HeavyProcessLatch.INSTANCE.isRunning() && myFuture == null) {
        myFuture = JobScheduler.getScheduler().schedule(() -> {
          if (HeavyProcessLatch.INSTANCE.isRunning() && myIndicator.isRunning()) myIndicator.cancel();
        }, myLongActivityDurationMs, TimeUnit.MILLISECONDS);
      }
    }

    private synchronized void doNotCancel() {
      if (!HeavyProcessLatch.INSTANCE.isRunning() && myFuture != null) {
        ScheduledFuture<?> future = myFuture;
        myFuture = null;
        future.cancel(true);
      }
    }
  }
}
